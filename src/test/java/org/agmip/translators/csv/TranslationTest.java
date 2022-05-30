package org.agmip.translators.csv;

import java.io.File;
import java.io.IOException;
import java.net.URL;
//import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for simple App.
 */
public class TranslationTest
{
    private static Logger LOG = LoggerFactory.getLogger(TranslationTest.class);
    private CSVInput importer;
    private URL simpleTest, ccTest, refTest, multiTest, asteriskTest, scdelimTest, multiTableTest;
    private URL zipTest;

    @Before
    public void setup() {
        importer = new CSVInput();
        simpleTest   = this.getClass().getResource("/test_1.csv");
        ccTest       = this.getClass().getResource("/commented_column.csv");
        refTest      = this.getClass().getResource("/ref_test.csv");
        multiTest    = this.getClass().getResource("/multiple_sections.csv");
        asteriskTest  = this.getClass().getResource("/one_ex_per.csv");
        zipTest      = this.getClass().getResource("/test.zip");
        scdelimTest = this.getClass().getResource("/sc_delim.csv");
        multiTableTest = this.getClass().getResource("/Soil_Profile.csv");
    }

    @Test
    public void SimpleTest() {
        Map result = new LinkedHashMap();
        Map compare = new LinkedHashMap();
        compare.put("exname", "Simple");
        compare.put("fldrs", "1.0");
        try {
            result = importer.readFile(simpleTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("SimpleTest results: "+result.toString());
    }

    @Test
    public void commentedColumnTest() {
        Map result = new LinkedHashMap();
        Map compare = new LinkedHashMap();
        Map weather = new LinkedHashMap();

        compare.put("exname", "Simple");
        compare.put("fldrs", "2.0");
        compare.put("wst_id", "abc123");
        weather.put("wst_id", "abc123");
        compare.put("weather", weather);

        try {
            result = importer.readFile(ccTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Commented column results: "+result.toString());
    }

    @Test
    public void referenceTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(refTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Reference test results: "+result.toString());
    }

    @Test
    public void multipleSectionsTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(multiTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Multiple sections test results: "+result.toString());
    }

    @Test
    public void oneExperimentPerLineTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(asteriskTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("One experiment per test results: "+result.toString());
    }
    
    @Test
    public void semicolonDelimiterTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(scdelimTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Semicolon Delimiter test results: "+result.toString());
    }
    
    @Test
    public void multiTableTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(multiTableTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Multi-Table test results: "+result.toString());
    }
}
