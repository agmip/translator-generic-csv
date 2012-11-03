package org.agmip.translators.csv;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.agmip.core.types.TranslatorInput;

public class DomeInput implements TranslatorInput {
    private static final Logger log = LoggerFactory.getLogger(DomeInput.class);
    private HashMap<String, ArrayList<HashMap<String,String>>> dome = new HashMap<String, ArrayList<HashMap<String,String>>>();

    public Map readFile(String fileName) throws Exception {
        if (fileName.toUpperCase().endsWith("CSV")) {
            readCSV(new FileInputStream(fileName));
        }
        return dome;
    }

    public void readCSV(InputStream stream) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        ArrayList<HashMap<String, String>> info  = new ArrayList<HashMap<String, String>>();
        ArrayList<HashMap<String, String>> rules = new ArrayList<HashMap<String, String>>();
        CSVReader reader = new CSVReader(br);
        String[] nextLine;
        int ln = 0;

        while ((nextLine = reader.readNext()) != null) {
            HashMap<String, String> lineMap = new HashMap<String, String>();
            ln++;
            if (nextLine[0].startsWith("&")) {
                // This is an official dome line.
                log.debug("Found a DOME instruction at line {}", ln);
                String cmd = nextLine[1].trim().toUpperCase();
                if (cmd.equals("INFO")) {
                    lineMap.put(nextLine[2].toLowerCase(), nextLine[3].toUpperCase());
                    info.add(lineMap);
                } else if ((cmd.equals("FILL") || cmd.equals("REPLACE"))) {
                    StringBuilder args = new StringBuilder();
                    if (nextLine[3].endsWith("()")) {
                        // Handle Arguments
                        int argLen = nextLine.length - 3;

                        if (argLen != 0) {
                            args.append(nextLine[3]);
                            if (argLen > 1) {
                                for( int i = 4; i < nextLine.length; i++) {
                                    args.append("|");  
                                    if (! nextLine[i].startsWith("!")) {
                                        args.append(nextLine[i].toUpperCase());
                                    }
                                }
                            }
                        }

                        log.debug("Current Args: {}", args.toString());
                        int chopIndex = args.indexOf("||", 0);
                        if (chopIndex == -1) {
                            chopIndex = args.length();
                        }
                        if (args.substring(0,chopIndex).endsWith("|")) {
                            chopIndex --;
                        }
                        lineMap.put("args", args.substring(0,chopIndex));
                    } else {
                        // Variable or static
                        lineMap.put("args", nextLine[3].toUpperCase());
                    }

                    lineMap.put("cmd", cmd);
                    lineMap.put("variable", nextLine[2]);
                    rules.add(lineMap);
                } else {
                    log.error("Found invalid command {} at line {}", cmd, ln);
                }
            } 
        }
        dome.put("info", info);
        dome.put("rules", rules);
    }

    public HashMap<String, ArrayList<HashMap<String,String>>> getDome() {
        return dome;
    }

    public ArrayList<HashMap<String, String>> getDomeInfo() {
        return dome.get("info");
    }

    public ArrayList<HashMap<String, String>> getDomeRules() {
        return dome.get("rules");
    }
}