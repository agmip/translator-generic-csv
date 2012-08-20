package org.agmip.translators.csv;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
import org.agmip.util.acepathfinder.AcePathfinderUtil.PathType;

/**
 * This class converts CSV formatted files into the AgMIP ACE JSON
 * format. It uses a common file pattern as described below.
 *
 * <b>First Column Descriptors</b>
 * <p># - Lines with the first column text containing only a "#" is considered 
 * a header row</p>
 * <p>! - Lines with the first column text containing only a "!" are considered
 * a comment and not parsed.
 *
 * The first header/datarow(s) are metadata (or global data) if there are multiple
 * rows of metadata, they are considered to be a collection of experiments. 
 *
 * <b>Single Experiment Example</b>
 * +-------+----------+---------+---------+
 * | #     + VAR_1    | VAR_2   | VAR_3   |
 * +-------+----------+---------+---------+
 * |       + A        | B       | C       |
 * +-------+----------+---------+---------+
 * |#      + VAR_X    | VAR_Y   | VAR_Z   |
 * +-------+----------+---------+---------+
 * |       + 9        | 8       | 7.0     |
 * +-------+----------+---------+---------+
 * |       | 2        | 3       | 3.4     |
 * +-------+----------+---------+---------+
 *
 *
 * <b>Multiple Experiment Example</b>
 * +-------+----------+---------+---------+
 * | #     + VAR_1    | VAR_2   | VAR_3   |
 * +-------+----------+---------+---------+
 * | 1     + A        | B       | C       |
 * +-------+----------+---------+---------+
 * | 2     + D        | E       | F       |
 +-------+----------+---------+---------+
 * |#      + VAR_X    | VAR_Y   | VAR_Z   |
 * +-------+----------+---------+---------+
 * | 1     + 9        | 8       | 7.0     |
 * +-------+----------+---------+---------+
 * | 1     | 2        | 3       | 3.4     |
 * +-------+----------+---------+---------+
 * | 2     + 5        | 6       | 7.0     |
 * +-------+----------+---------+---------+
 * | 2     | 4        | 3       | 2.1     |
 * +-------+----------+---------+---------+
 *
 * TODO:
 * * Add Zip File capabilities
 * * Flesh out multiple weather and soils compatabilities
 *   {multi: {experiments:[], weathers:[], soils:[]}}
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

    private enum LineType {
        COMMENT,  // !
            SUMMARY,  // #
            SERIES,   // %
            COMPLETE, // *
            DATA      // <anything else>
    }

    private static class CSVHeader {
        private final ArrayList<String> headers;
        private final ArrayList<Integer> skippedColumns; 

        public CSVHeader(ArrayList<String> headers, ArrayList<Integer> sc) {
            this.headers = headers;
            this.skippedColumns = sc;
        }

        public CSVHeader() {
            this.headers = new ArrayList();
            this.skippedColumns = new ArrayList();
        }

        public ArrayList<String> getHeaders() {
            return headers;
        }

        public ArrayList<Integer> getSkippedColumns() {
            return skippedColumns;
        }
    }

    public CSVInput() {
        expMap     = new LinkedHashMap();
        weatherMap = new LinkedHashMap();
        soilMap    = new LinkedHashMap();
        idMap      = new LinkedHashMap();
        finalMap   = new LinkedHashMap();
        finalMap.put("experiments", expMap);
        finalMap.put("weather", weatherMap);
        finalMap.put("soil", soilMap);
    }

    public Map readFile(String fileName) throws IOException {
        if (fileName.toUpperCase().endsWith("CSV")) {
            readCSV(new FileInputStream(fileName));
        } else if (fileName.toUpperCase().endsWith("ZIP")) {
            //Handle a ZipInputStream instead
            LOG.debug("Launching zip file handler");
            ZipFile zf = new ZipFile(fileName);
            Enumeration e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = (ZipEntry) e.nextElement();
                LOG.debug("Entering file: "+ze);
                readCSV(zf.getInputStream(ze));
            }
            zf.close();
        }
        return cleanUpFinalMap();
    }


    protected void readCSV(InputStream fileStream) throws IOException {
        HeaderType section = HeaderType.UNKNOWN;
        LineType status;
        CSVHeader currentHeader = new CSVHeader();
        String[] nextLine;
        CSVReader reader = new CSVReader(new InputStreamReader(fileStream));
        // Clear out the idMap for every file created.
        idMap.clear();
        int ln = 0;

        while ((nextLine = reader.readNext()) != null) {
            ln++;
            LOG.debug("Line number: "+ln);
            if (nextLine[0].startsWith("!")) {
                LOG.debug("Found a comment line");
                continue;
            } else if (nextLine[0].startsWith("#")) {
                LOG.debug("Found a summary header line");
                status = LineType.SUMMARY;
                section = HeaderType.SUMMARY;
                currentHeader = parseHeaderLine(nextLine);
            } else if (nextLine[0].startsWith("%")) {
                LOG.debug("Found a series header line");
                status  = LineType.SERIES;
                section = HeaderType.SERIES;
                currentHeader = parseHeaderLine(nextLine);
            } else if (nextLine[0].startsWith("*")) {
                LOG.debug("Found a complete experiment line");
                status = LineType.COMPLETE;
                section = HeaderType.SUMMARY;
                parseDataLine(currentHeader, section, nextLine, true);
            } else if (nextLine.length == 1) {
                LOG.debug("Found a blank line, skipping");
            } else {
                LOG.debug("Found a data line with ["+nextLine[0]+"] as the index");
                status = LineType.DATA;
                parseDataLine(currentHeader, section, nextLine, false);
            }
        }
    }

    protected CSVHeader parseHeaderLine(String[] data) {
        ArrayList<String> h = new ArrayList();
        ArrayList<Integer> sc = new ArrayList();

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

    protected void parseDataLine(CSVHeader header, HeaderType section, String[] data, boolean isComplete) {
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
                String var = data[i];
                i++;
                String val = data[i];
                LOG.debug("Trimmed var: "+var.trim()+" and length: "+var.trim().length());
                if (var.trim().length() != 0) {
                    LOG.debug("INSERTING!");
                    insertValue(dataIndex, var, val);
                } else {
                    LOG.debug("WTF!");
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
                        String var = headers.get(i);
                        insertValue(dataIndex, headers.get(i), data[i+1]);
                    }
                }
            }
        }
    }

    protected void insertValue(String index, String variable, String value) {
        String var = variable.toLowerCase();
        LinkedHashMap<String, LinkedHashMap<String, Object>> topMap;
        if (var.equals("wst_id") || var.equals("soil_id")) {
            insertIndex(expMap, index);
            LinkedHashMap<String, Object> temp = expMap.get(index);
            temp.put(var, value);
        }
        switch (AcePathfinderUtil.getVariableType(var)) {
            case WEATHER:
                topMap = weatherMap;
                break;
            case SOIL:
                topMap = soilMap;
                break;
            default:
                topMap = expMap;
                break;
        }
        insertIndex(topMap, index);
        LinkedHashMap<String, Object> currentMap = topMap.get(index);
        AcePathfinderUtil.insertValue(currentMap, var, value); 
    }

    protected void insertIndex(LinkedHashMap map, String index) {
        if (! map.containsKey(index)) {
            map.put(index, new LinkedHashMap());
        }
    }

    protected LinkedHashMap cleanUpFinalMap() {
        ArrayList<String> toRemove = new ArrayList();
        LinkedHashMap<String, ArrayList<LinkedHashMap<String, Object>>> base = new LinkedHashMap();
        ArrayList<LinkedHashMap<String, Object>> experiments = new ArrayList();
        ArrayList<LinkedHashMap<String, Object>> weathers = new ArrayList();
        ArrayList<LinkedHashMap<String, Object>> soils = new ArrayList();

        for (Map.Entry<String, LinkedHashMap<String, Object>> entry : expMap.entrySet()) {
            String key = entry.getKey();
            LinkedHashMap ex = entry.getValue();
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
                LinkedHashMap<String, Object> temp = (LinkedHashMap) wth;
                if( temp.containsKey("weather") ) {
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
                LinkedHashMap<String, Object> temp = (LinkedHashMap) sl;
                if( temp.containsKey("soil") ) {
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
