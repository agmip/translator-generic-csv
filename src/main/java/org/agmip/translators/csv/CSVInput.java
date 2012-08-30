package org.agmip.translators.csv;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import au.com.bytecode.opencsv.CSVReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.agmip.core.types.TranslatorInput;
import org.agmip.util.acepathfinder.*;

/**
 * This class converts CSV formatted files into the AgMIP ACE JSON
 * format. It uses a common file pattern as described below.
 *
 * <p><b>First Column Descriptors</b></p>
 * <p># - Lines with the first column text containing only a "#" is considered 
 * a header row</p>
 * <p>! - Lines with the first column text containing only a "!" are considered
 * a comment and not parsed.
 *
 * The first header/datarow(s) are metadata (or global data) if there are multiple
 * rows of metadata, they are considered to be a collection of experiments. 
 *
 */

public class CSVInput implements TranslatorInput {
	private static Logger LOG = LoggerFactory.getLogger(CSVInput.class);
	private LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Object>>> finalMap;
	private LinkedHashMap<String, LinkedHashMap<String, Object>>expMap, weatherMap, soilMap; // Storage maps
	private LinkedHashMap<String, String> idMap;

	private enum HeaderType {
		UNKNOWN, // Probably uninitialized
		SUMMARY, // #
		SERIES   // %
	}

	private static class CSVHeader {
		private final ArrayList<String> headers;
		private final ArrayList<Integer> skippedColumns; 

		public CSVHeader(ArrayList<String> headers, ArrayList<Integer> sc) {
			this.headers = headers;
			this.skippedColumns = sc;
		}

		public CSVHeader() {
			this.headers = new ArrayList<String>();
			this.skippedColumns = new ArrayList<Integer>();
		}

		public ArrayList<String> getHeaders() {
			return headers;
		}

		public ArrayList<Integer> getSkippedColumns() {
			return skippedColumns;
		}
	}

	public CSVInput() {
		expMap     = new LinkedHashMap<String, LinkedHashMap<String, Object>>();
		weatherMap = new LinkedHashMap<String, LinkedHashMap<String, Object>>();
		soilMap    = new LinkedHashMap<String, LinkedHashMap<String, Object>>();
		idMap      = new LinkedHashMap<String, String>();
		finalMap   = new LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Object>>>();
		finalMap.put("experiments", expMap);
		finalMap.put("weather", weatherMap);
		finalMap.put("soil", soilMap);
	}

	public Map readFile(String fileName) throws Exception {
		if (fileName.toUpperCase().endsWith("CSV")) {
			readCSV(new FileInputStream(fileName));
		} else if (fileName.toUpperCase().endsWith("ZIP")) {
			//Handle a ZipInputStream instead
			LOG.debug("Launching zip file handler");
			ZipFile zf = new ZipFile(fileName);
			Enumeration<? extends ZipEntry> e = zf.entries();
			while (e.hasMoreElements()) {
				ZipEntry ze = (ZipEntry) e.nextElement();
				LOG.debug("Entering file: "+ze);
				readCSV(zf.getInputStream(ze));
			}
			zf.close();
		}
		return cleanUpFinalMap();
	}


	protected void readCSV(InputStream fileStream) throws Exception {
		HeaderType section = HeaderType.UNKNOWN;
		CSVHeader currentHeader = new CSVHeader();
		String[] nextLine;
		CSVReader reader = new CSVReader(new InputStreamReader(fileStream));
		// Clear out the idMap for every file created.
		idMap.clear();
		int ln = 0;
		LOG.debug(AcePathfinder.INSTANCE.peekAtDatefinder().toString());

		while ((nextLine = reader.readNext()) != null) {
			ln++;
			LOG.debug("Line number: "+ln);
			if (nextLine[0].startsWith("!")) {
				LOG.debug("Found a comment line");
				continue;
			} else if (nextLine[0].startsWith("#")) {
				LOG.debug("Found a summary header line");
				section = HeaderType.SUMMARY;
				currentHeader = parseHeaderLine(nextLine);
			} else if (nextLine[0].startsWith("%")) {
				LOG.debug("Found a series header line");
				section = HeaderType.SERIES;
				currentHeader = parseHeaderLine(nextLine);
			} else if (nextLine[0].startsWith("*")) {
				LOG.debug("Found a complete experiment line");
				section = HeaderType.SUMMARY;
				parseDataLine(currentHeader, section, nextLine, true);
			} else if (nextLine.length == 1) {
				LOG.debug("Found a blank line, skipping");
			} else {
				LOG.debug("Found a data line with ["+nextLine[0]+"] as the index");
				parseDataLine(currentHeader, section, nextLine, false);
			}
		}
		reader.close();
	}

	protected CSVHeader parseHeaderLine(String[] data) {
		ArrayList<String> h = new ArrayList<String>();
		ArrayList<Integer> sc = new ArrayList<Integer>();

		int l = data.length;
		for (int i=1; i < l; i++) {
			if (data[i].startsWith("!")) {
				sc.add(i);
			}
			if (data[i].trim().length() != 0) {
				h.add(data[i]);
			}
		}
		return new CSVHeader(h, sc);
	}

	protected void parseDataLine(CSVHeader header, HeaderType section, String[] data, boolean isComplete) throws Exception {
		ArrayList<String> headers = header.getHeaders();
		int l = headers.size();
		String dataIndex;
		dataIndex = UUID.randomUUID().toString();

		if (! isComplete) {
			if (idMap.containsKey(data[0]) ) {
				dataIndex = idMap.get(data[0]);
			} else {
				idMap.put(data[0], dataIndex);
			}
		}
		if (data[1].toLowerCase().equals("event")) {
			for (int i=3; i < l; i++) {
				String var = data[i].toLowerCase();
				i++;
				String val = data[i];
				LOG.debug("Trimmed var: "+var.trim()+" and length: "+var.trim().length());
				if (var.trim().length() != 0) {
					LOG.debug("INSERTING!");
					insertValue(dataIndex, var, val);
				}
			}
		} else if (header.getSkippedColumns().isEmpty()) {
			for (int i=0; i < l; i++) {
				if (! data[i+1].equals("")) {
					insertValue(dataIndex, headers.get(i), data[i+1]);
				}
			}
		} else {
			ArrayList<Integer> skipped = header.getSkippedColumns();
			for (int i=0; i < l; i++) {
				if (! data[i+1].equals("")) {
					if (! skipped.contains(i+1)) {
						insertValue(dataIndex, headers.get(i), data[i+1]);
					}
				}
			}
		}
	}

	protected void insertValue(String index, String variable, String value) throws Exception {
		String var = variable.toLowerCase();
		LinkedHashMap<String, LinkedHashMap<String, Object>> topMap;
		if (var.equals("wst_id") || var.equals("soil_id")) {
			insertIndex(expMap, index);
			LinkedHashMap<String, Object> temp = expMap.get(index);
			temp.put(var, value);
		} else {
			if (AcePathfinder.INSTANCE.isDate(var)) {
				try {
					LOG.debug("Converting date from: "+value);
					value = value.replace("/", "-");
					DateFormat f = new SimpleDateFormat("yyyymmdd");
					Date d = new SimpleDateFormat("yyyy-mm-dd").parse(value);
					value = f.format(d);
					LOG.debug("Converting date to: "+value);
				} catch (Exception ex) {
					throw new Exception(ex);
				}
			}
		}
		switch (AcePathfinderUtil.getVariableType(var)) {
		case WEATHER:
			topMap = weatherMap;
			break;
		case SOIL:
			topMap = soilMap;
			break;
		case UNKNOWN:
			LOG.error("Skipping unknown variable: ["+var+"]");
			return;
		default:
			topMap = expMap;
			break;
		}
		insertIndex(topMap, index);
		LinkedHashMap<String, Object> currentMap = topMap.get(index);
		AcePathfinderUtil.insertValue(currentMap, var, value); 
	}

	protected void insertIndex(LinkedHashMap<String, LinkedHashMap<String, Object>> map, String index) {
		if (! map.containsKey(index)) {
			map.put(index, new LinkedHashMap<String, Object>());
		}
	}

	protected LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>> cleanUpFinalMap() {
		LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>> base = new LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>>();
		ArrayList<LinkedHashMap<String, Object>> experiments = new ArrayList<LinkedHashMap<String,Object>>();
		ArrayList<LinkedHashMap<String, Object>> weathers = new ArrayList<LinkedHashMap<String,Object>>();
		ArrayList<LinkedHashMap<String, Object>> soils = new ArrayList<LinkedHashMap<String,Object>>();

		for (LinkedHashMap<String, Object> ex : expMap.values()) {
			ex.remove("weather");
			ex.remove("soil");
			if (ex.size() == 2 && ex.containsKey("wst_id") && ex.containsKey("soil_id"))  {
			} else if (ex.size() == 1 && (ex.containsKey("wst_id") || ex.containsKey("soil_id"))) {
			} else {
				experiments.add(ex);
			}
		}

		for (Object wth : weatherMap.values()) {
			if (wth instanceof LinkedHashMap) {
				@SuppressWarnings("unchecked")
				LinkedHashMap<String, Object> temp = (LinkedHashMap<String, Object>) wth;
				if( temp.containsKey("weather") ) {
					@SuppressWarnings("unchecked")
					LinkedHashMap<String, Object> weather = (LinkedHashMap<String, Object>) temp.get("weather");
					if (weather.size() == 1 && weather.containsKey("wst_id")) {
					} else {
						weathers.add(weather);
					}
				}
			}
		}

		for (Object sl : soilMap.values()) {
			if (sl instanceof LinkedHashMap) {
				@SuppressWarnings("unchecked")
				LinkedHashMap<String, Object> temp = (LinkedHashMap<String, Object>) sl;
				if( temp.containsKey("soil") ) {
					@SuppressWarnings("unchecked")
					LinkedHashMap<String, Object> soil = (LinkedHashMap<String, Object>) temp.get("soil");
					if (soil.size() == 1 && soil.containsKey("soil_id")) {
					} else {
						soils.add(soil);
					}
				}
			}
		}

		base.put("experiments", experiments);
		base.put("weathers", weathers);
		base.put("soils", soils);
		return base;
	}
}
