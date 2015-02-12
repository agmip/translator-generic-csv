package org.agmip.translators.csv;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import au.com.bytecode.opencsv.CSVReader;
import java.io.IOException;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.agmip.ace.AcePathfinder;
import org.agmip.ace.LookupCodes;
import org.agmip.ace.util.AcePathfinderUtil;
import org.agmip.common.Functions;
import org.agmip.core.types.TranslatorInput;

/**
 * This class converts CSV formatted files into the AgMIP ACE JSON format. It
 * uses a common file pattern as described below.
 *
 * <p><b>First Column Descriptors</b></p> <p># - Lines with the first column
 * text containing only a "#" is considered a header row</p> <p>! - Lines with
 * the first column text containing only a "!" are considered a comment and not
 * parsed.
 *
 * The first header/datarow(s) are metadata (or global data) if there are
 * multiple rows of metadata, they are considered to be a collection of
 * experiments.
 *
 */
public class CSVInput implements TranslatorInput {

    private static Logger LOG = LoggerFactory.getLogger(CSVInput.class);
//    private HashMap<String, HashMap<String, HashMap<String, Object>>> finalMap;
    private HashMap<String, HashMap<String, Object>> expMap, weatherMap, soilMap; // Storage maps
    private HashMap<String, Integer> trtTracker;
    private HashMap<String, String> idMap;
    private ArrayList<String> orderring;
    private String listSeparator;
    private AcePathfinder pathfinder = AcePathfinderUtil.getInstance();
    private static HashSet unknowVars = new HashSet();
    private boolean isAgTrails = false;
    private boolean isAgTrailsExist = false;

    private enum HeaderType {

        UNKNOWN, // Probably uninitialized
        SUMMARY, // #
        SERIES,   // %
        AT_META,
        AT_DATA
    }

    private static class CSVHeader {

        private final ArrayList<String> headers;
        private final ArrayList<Integer> skippedColumns;
        private final String defPath;
        private final AcePathfinderUtil.PathType defPathType;
        private HashMap<String, String> agtrMapping;
        private int agrtVarCol = -1;
        private int agrtValCol = -1;

        public CSVHeader(ArrayList<String> headers, ArrayList<Integer> sc) {
            this(headers, sc, null, AcePathfinderUtil.PathType.UNKNOWN);
        }

        public CSVHeader(ArrayList<String> headers, ArrayList<Integer> sc, String defPath, AcePathfinderUtil.PathType defPathType) {
            this.headers = headers;
            this.skippedColumns = sc;
            this.defPath = defPath;
            this.defPathType = defPathType;
        }
        
        public CSVHeader(ArrayList<String> headers, ArrayList<Integer> sc, String defPath, AcePathfinderUtil.PathType defPathType, HashMap<String, String> mapping) {
            this(headers, sc, defPath, defPathType);
            this.agtrMapping = mapping;
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equals("variables measured")) {
                    this.agrtVarCol = i + 1;
                } else if (headers.get(i).equals("value")) {
                    this.agrtValCol = i + 1;
                }
            }
            if (this.agrtVarCol < 0) {
                this.agrtVarCol = 2;
            }
            if (this.agrtValCol < 0) {
                this.agrtValCol = 3;
            }
        }

        public CSVHeader() {
            this.headers = new ArrayList<String>();
            this.skippedColumns = new ArrayList<Integer>();
            this.defPath = null;
            this.defPathType = AcePathfinderUtil.PathType.UNKNOWN;
        }

        public ArrayList<String> getHeaders() {
            return headers;
        }

        public ArrayList<Integer> getSkippedColumns() {
            return skippedColumns;
        }

        public String getDefPath() {
            return defPath;
        }

        public AcePathfinderUtil.PathType getDefPathType() {
            return defPathType;
        }

        public HashMap<String, String> getAgtrMapping() {
            return this.agtrMapping;
        }

        public int getAgtrVarCol() {
            return this.agrtVarCol;
        }

        public int getAgtrValCol() {
            return this.agrtValCol;
        }
    }

    public CSVInput() {
        expMap = new HashMap<String, HashMap<String, Object>>();
        weatherMap = new HashMap<String, HashMap<String, Object>>();
        soilMap = new HashMap<String, HashMap<String, Object>>();
        trtTracker = new HashMap<String, Integer>();
        idMap = new HashMap<String, String>();

        orderring = new ArrayList<String>();
//        finalMap = new HashMap<String, HashMap<String, HashMap<String, Object>>>();
        this.listSeparator = ",";
//        finalMap.put("experiments", expMap);
//        finalMap.put("weather", weatherMap);
//        finalMap.put("soil", soilMap);
    }

    @Override
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
                LOG.debug("Entering file: " + ze);
                if (ze.getName().toLowerCase().endsWith(".csv")) {
                    readCSV(zf.getInputStream(ze));
                }
            }
            zf.close();
        }
        return cleanUpFinalMap();
    }

    protected void readCSV(InputStream fileStream) throws Exception {
        HeaderType section = HeaderType.UNKNOWN;
        CSVHeader currentHeader = new CSVHeader();
        String[] nextLine;
        BufferedReader br = new BufferedReader(new InputStreamReader(fileStream));

        // Check to see if this is an international CSV. (;, vs ,.)
        this.isAgTrails = false;
        HashMap<String, String> mapping = null;
        setListSeparator(br);
        CSVReader reader = new CSVReader(br, this.listSeparator.charAt(0));
        if (this.isAgTrails) {
            mapping = readAgTrail2AgMipMap();
        }

        // Clear out the idMap for every file created.
        idMap.clear();
        int ln = 0;

        while ((nextLine = reader.readNext()) != null) {
            ln++;
            LOG.debug("Line number: " + ln);
            if (isAgTrails && nextLine[0].equals("Replication")) {
                LOG.debug("Found an AgTrails meta header line");
                section = HeaderType.AT_DATA;
                currentHeader = parseHeaderLineForAgTrails(nextLine, mapping);
            } else if (isAgTrails && nextLine[0].equals("Id Trials")) {
                LOG.debug("Found an AgTrails data header line");
                section = HeaderType.AT_META;
                currentHeader = parseHeaderLineForAgTrails(nextLine, mapping);
            } else if (isAgTrails) {
                LOG.debug("Found an AgTrails line");
                parseDataLine(currentHeader, section, nextLine, true);
            } else if (nextLine[0].startsWith("!")) {
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
            } else if (nextLine[0].startsWith("&")) {
                LOG.debug("Found a DOME line, skipping");
            } else if (nextLine.length == 1) {
                LOG.debug("Found a blank line, skipping");
            } else {
                boolean isBlank = true;
                // Check the nextLine array for all blanks
                int nlLen = nextLine.length;
                for (int i = 0; i < nlLen; i++) {
                    if (!nextLine[i].equals("")) {
                        isBlank = false;
                        break;
                    }
                }
                if (!isBlank) {
                    LOG.debug("Found a data line with [" + nextLine[0] + "] as the index");
                    parseDataLine(currentHeader, section, nextLine, false);
                } else {
                    LOG.debug("Found a blank line, skipping");
                }
            }
        }
        reader.close();
    }

    protected CSVHeader parseHeaderLine(String[] data) {
        ArrayList<String> h = new ArrayList<String>();
        ArrayList<Integer> sc = new ArrayList<Integer>();
        String defPath = null;
        AcePathfinderUtil.PathType defPathType = AcePathfinderUtil.PathType.UNKNOWN;

        int l = data.length;
        for (int i = 1; i < l; i++) {
            if (data[i].startsWith("!")) {
                sc.add(i);
            }
            if (data[i].trim().length() != 0) {
                h.add(data[i]);
            }
            if (defPath == null) {
                defPath = AcePathfinderUtil.getInstance().getPath(data[i].trim());
                if (defPath != null) {
                    defPath = defPath.replaceAll(",", "").trim();
                    defPathType = AcePathfinderUtil.getVariableType(data[i].trim());
                }
            }
        }
        return new CSVHeader(h, sc, defPath, defPathType);
    }

    protected CSVHeader parseHeaderLineForAgTrails(String[] data, HashMap<String, String> mapping) {
        ArrayList<String> h = new ArrayList<String>();
        ArrayList<Integer> sc = new ArrayList<Integer>();
        String defPath = null;
        AcePathfinderUtil.PathType defPathType = AcePathfinderUtil.PathType.UNKNOWN;

        int l = data.length;
        for (int i = 1; i < l; i++) {
            String var = mapping.get(data[i].toLowerCase());
            if (var == null) {
                sc.add(i);
                var = data[i].trim().toLowerCase();
            }
            if (var.length() != 0) {
                h.add(var);
            }
            if (defPath == null) {
                defPath = AcePathfinderUtil.getInstance().getPath(var);
                if (defPath != null) {
                    defPath = defPath.replaceAll(",", "").trim();
                    defPathType = AcePathfinderUtil.getVariableType(var);
                } else if (data[0].equals("Replication")) {
                    defPathType = AcePathfinderUtil.PathType.EXPERIMENT;
                }
            }
        }
        return new CSVHeader(h, sc, defPath, defPathType, mapping);
    }

    protected HashMap<String, String> readAgTrail2AgMipMap() {
        HashMap<String, String> mapping = new HashMap();
        InputStream res = getClass().getClassLoader().getResourceAsStream("AgTrials2AgMIPVarMap.csv");
        try {
            if( res != null ) {
                CSVReader reader = new CSVReader(new InputStreamReader(res));
                // Get AgTrails and AgMaps column number
                String[] line = reader.readNext();
                int agtrailsCol = 1;
                int agmipCol = 2;
                for (int i = 0; i < line.length; i++) {
                    if (line[i].startsWith("AgTrials")) {
                        agtrailsCol = i;
                    } else if (line[i].startsWith("ICASA")) {
                        agmipCol = i;
                    }
                }
                // Read mapping definition
                while(( line = reader.readNext()) != null) {
                    String agtrailsVar = line[agtrailsCol].toLowerCase();
                    String agmipVar = line[agmipCol].toLowerCase();
                    if (agtrailsVar.contains(";")) {
                        String[] agtrailsVars = agtrailsVar.split(";");
                        for (String var : agtrailsVars) {
                            mapping.put(var, agmipVar);
                        }
                    } else {
                        mapping.put(agtrailsVar, agmipVar);
                    }
                }
                reader.close();
            } else {
                LOG.error("Missing embedded CSV file for configuration. no AgTrails variable will be recognized");
            }
        } catch(IOException ex) {
            LOG.debug(ex.toString());
            throw new RuntimeException(ex);
        }

        return mapping;
    }

    protected void parseDataLine(CSVHeader header, HeaderType section, String[] data, boolean isComplete) throws Exception {
        ArrayList<String> headers = header.getHeaders();
        int l = headers.size();
        String dataIndex;
        dataIndex = UUID.randomUUID().toString();
        if (section.equals(HeaderType.AT_DATA)) {
            isComplete = false;
        } else if (section.equals(HeaderType.AT_META)) {
            dataIndex = "AgTrails_" + dataIndex;
        }

        if (!isComplete) {
            if (idMap.containsKey(data[0])) {
                dataIndex = idMap.get(data[0]);
            } else {
                idMap.put(data[0], dataIndex);
            }
        }
        if (section.equals(HeaderType.AT_DATA)) {
            int varCol = header.getAgtrVarCol();
            int valCol = header.getAgtrValCol();
            String var = header.getAgtrMapping().get(data[varCol]);
            String val = converAgtrData(var, data[valCol]);
            if (var == null) {
                LOG.warn("{} is not defined in the mapping", data[varCol]);
            } else {
                insertValue(dataIndex, var, val, header);
            }
            
        } else if (data[1].toLowerCase().equals("event")) {
            for (int i = 3; i < data.length; i++) {
                String var = data[i].toLowerCase();
                i++;
                if (i < data.length) {
                    String val = data[i];
                    LOG.debug("Trimmed var: " + var.trim() + " and length: " + var.trim().length());
                    if (var.trim().length() != 0 && val.trim().length() != 0) {
                        LOG.debug("INSERTING! Var: " + var + " Val: " + val);
                        insertValue(dataIndex, var, val, header);
                    }
                }
            }
            LOG.debug("Leaving event loop");
        } else if (header.getSkippedColumns().isEmpty()) {
            for (int i = 0; i < l; i++) {
                if (!data[i + 1].equals("")) {
                    if (section.equals(HeaderType.AT_META)) {
                        insertValue(dataIndex, headers.get(i), converAgtrMeta(headers.get(i), data[i + 1]), header);
                    } else {
                        insertValue(dataIndex, headers.get(i), data[i + 1], header);
                    }
                }
            }
        } else {
            ArrayList<Integer> skipped = header.getSkippedColumns();
            for (int i = 0; i < l; i++) {
                if (!data[i + 1].equals("")) {
                    if (!skipped.contains(i + 1)) {
                        if (section.equals(HeaderType.AT_META)) {
                            insertValue(dataIndex, headers.get(i), converAgtrMeta(headers.get(i), data[i + 1]), header);
                        } else {
                            insertValue(dataIndex, headers.get(i), data[i + 1], header);
                        }
                    }
                }
            }
        }
    }

    protected void insertValue(String index, String variable, String value, CSVHeader header) throws Exception {
        try {
            String var = variable.toLowerCase();
            HashMap<String, HashMap<String, Object>> topMap = null;
            if (var.equals("wst_id") || var.equals("soil_id")) {
                insertIndex(expMap, index, true);
                HashMap<String, Object> temp = expMap.get(index);
                temp.put(var, value);
            } else if (var.equals("exname")) {
                Integer i = 0;
                if (trtTracker.containsKey(value)) {
                    i = trtTracker.get(value);
                }
                i = i + 1;
                trtTracker.put(value, i);
                value = value + "_" + i;
            } else {
                if (pathfinder.isDate(var)) {
                    LOG.debug("Converting date from: " + value);
                    value = value.replace("/", "-");
                    DateFormat f = new SimpleDateFormat("yyyymmdd");
                    Date d = new SimpleDateFormat("yyyy-mm-dd").parse(value);
                    value = f.format(d);
                    LOG.debug("Converting date to: " + value);

                }
            }
            boolean isExperimentMap = false;
            String path = null;
            switch (AcePathfinderUtil.getVariableType(var)) {
                case WEATHER:
                    topMap = weatherMap;
                    break;
                case SOIL:
                    topMap = soilMap;
                    break;
                case UNKNOWN:
                    switch (header.getDefPathType()) {
                        case WEATHER:
                            topMap = weatherMap;
                            break;
                        case SOIL:
                            topMap = soilMap;
                            break;
                    }
                    path = header.getDefPath();
                    if (!unknowVars.contains(var)) {
                        if (path != null || "".equals(path)) {
                            LOG.warn("Putting unknow variable into [{}] section: [{}]", path, var);
                        } else {
                            LOG.warn("Putting unknow variable into root: [{}]", var);
                        }
                        unknowVars.add(var);
                    }
                    if (topMap != null) {
                        break;
                    }
                default:
                    isExperimentMap = true;
                    topMap = expMap;
                    break;
            }
            insertIndex(topMap, index, isExperimentMap);
            HashMap<String, Object> currentMap = topMap.get(index);
            AcePathfinderUtil.insertValue(currentMap, var, value, path, true);
        } catch (Exception ex) {
            throw new Exception(ex);
        }
    }

    protected void insertIndex(HashMap<String, HashMap<String, Object>> map, String index, boolean isExperimentMap) {
        if (!map.containsKey(index)) {
            map.put(index, new HashMap<String, Object>());
            if (isExperimentMap) {
                orderring.add(index);
            }

        }
    }
    
    private String converAgtrMeta(String var, String val) {
        if (var.equals("pdate") || var.equals("hdate")) { // TODO
            if (val.length() == 5) {
                try {
                    long date = (Long.parseLong(val) - 25568l) * 86400000l;
                    return Functions.convertToAgmipDateString(new Date(date));
                } catch (Exception e) {
                    LOG.warn("Invalid number of days since 1970/01/01 for [{}]: {}", var, val);
                    LOG.warn(Functions.getStackTrace(e));
                    return val;
                }
            } else if (val.matches("\\d{4}[\\\\\\-/]\\d{2}[\\\\\\-/]\\d{2}")) {
                return val.replaceAll("[\\\\\\-/]{1}", "");
            } else {
                return val;
            }
        } else if (var.equals("crid")) {
            return LookupCodes.lookupCode("CRID", val, "code", "APSIM").toUpperCase(); // TODO
        } else {
            return val;
        }
    }
    
    private String converAgtrData(String var, String val) {
        if (var.equals("hwah") || var.equals("chtx")) {
            return Functions.divide(val, "100");
        } else if (var.equals("hwah")) {
            return Functions.product(val, "1000", "0.875");
        } else if (var.equals("plpop")) {
            return ""; // TODO
        } else if (var.equals("plrs")) {
            return ""; // TODO
        } else if (var.equals("hwah")) {
            return "";
        } else {
            return val;
        }
    }

    protected HashMap<String, ArrayList<HashMap<String, Object>>> cleanUpFinalMap() {
        HashMap<String, ArrayList<HashMap<String, Object>>> base = new HashMap<String, ArrayList<HashMap<String, Object>>>();
        ArrayList<HashMap<String, Object>> experiments = new ArrayList<HashMap<String, Object>>();
        ArrayList<HashMap<String, Object>> weathers = new ArrayList<HashMap<String, Object>>();
        ArrayList<HashMap<String, Object>> soils = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> metaMap = null;

        if (this.isAgTrailsExist) {
            for (int i = 0; i < orderring.size(); i++) {
                if (orderring.get(i).startsWith("AgTrails_")) {
                    String metaId = orderring.remove(i);
                    metaMap = expMap.remove(metaId);
                    break;
                }
            }
            metaMap.put("data_source", "AgTrails");
        }
        
        for (String id : orderring) {
            //for (HashMap<String, Object> ex : expMap.values()) {
            HashMap<String, Object> ex;
            if (this.isAgTrailsExist) {
                ex = combineMap(metaMap, expMap.get(id));
            } else {
                ex = expMap.get(id);
            }
            ex.remove("weather");
            ex.remove("soil");
            if (ex.size() == 2 && ex.containsKey("wst_id") && ex.containsKey("soil_id")) {
            } else if (ex.size() == 1 && (ex.containsKey("wst_id") || ex.containsKey("soil_id"))) {
            } else {
                experiments.add(ex);
            }
        }

        for (Object wth : weatherMap.values()) {
            if (wth instanceof HashMap) {
                @SuppressWarnings("unchecked")
                HashMap<String, Object> temp = (HashMap<String, Object>) wth;
                if (temp.containsKey("weather")) {
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> weather = (HashMap<String, Object>) temp.get("weather");
                    if (weather.size() == 1 && weather.containsKey("wst_id")) {
                    } else {
                        weathers.add(weather);
                    }
                }
            }
        }

        for (Object sl : soilMap.values()) {
            if (sl instanceof HashMap) {
                @SuppressWarnings("unchecked")
                HashMap<String, Object> temp = (HashMap<String, Object>) sl;
                if (temp.containsKey("soil")) {
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> soil = (HashMap<String, Object>) temp.get("soil");
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
    
    private HashMap<String, Object> combineMap(HashMap<String, Object> base, HashMap<String, Object> in) {
        HashMap<String, Object> ret = new HashMap(base);
        for (Map.Entry<String, Object> entry : in.entrySet()) {
            Object orgVal = ret.get(entry.getKey());
            if (orgVal == null || entry.getValue() instanceof String) {
                ret.put(entry.getKey(), entry.getValue());
            } else if (entry.getValue() instanceof ArrayList) {
                if (ret.get(entry.getKey()) instanceof ArrayList) {
                    ((ArrayList) orgVal).addAll((ArrayList) entry.getValue());
                } else {
                    ret.put(entry.getKey(), entry.getValue());
                }
            } else if (entry.getValue() instanceof HashMap) {
                combineMap((HashMap<String, Object>) orgVal, (HashMap<String, Object>) entry.getValue());
            } else {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    protected void setListSeparator(BufferedReader in) throws Exception {
        // Set a mark at the beginning of the file, so we can get back to it.
        in.mark(7168);
        String sample;
        while ((sample = in.readLine()) != null) {
            if (sample.startsWith("#")) {
                String listSeperator = sample.substring(1, 2);
                LOG.debug("FOUND SEPARATOR: " + listSeperator);
                this.listSeparator = listSeperator;
                break;
            } else if (sample.startsWith("Replication")) {
                String listSeperator = sample.substring(11, 12);
                LOG.debug("FOUND SEPARATOR: " + listSeperator);
                this.listSeparator = listSeperator;
                this.isAgTrails = true;
                break;
            } else if (sample.startsWith("Id Trials")) {
                String listSeperator = sample.substring(9, 10);
                LOG.debug("FOUND SEPARATOR: " + listSeperator);
                this.listSeparator = listSeperator;
                this.isAgTrails = true;
                this.isAgTrailsExist = true;
                break;
            }
        }
        in.reset();
    }
}
