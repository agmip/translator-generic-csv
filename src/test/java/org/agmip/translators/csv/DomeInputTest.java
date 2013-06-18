package org.agmip.translators.csv;

import java.net.URL;
//import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DomeInputTest {
    private static final Logger log = LoggerFactory.getLogger(DomeInputTest.class);
    private DomeInput translator;
    private URL resource = null;

    @Before
    public void setup() {
        translator = new DomeInput();
    }

    @Test
    public void simpleReaderTest() {
        resource = this.getClass().getResource("/sample_dome.csv");
        try {
            Map dome = translator.readFile(resource.getPath());
            log.info("Translation results: {}", dome.toString());
        } catch (Exception ex) {
            assertTrue(false);
        }
        assertTrue(true);
    }

    @Test
    public void domeReaderTest() {
        resource = this.getClass().getResource("/dome_test_v1_5.csv");
        try {
            Map dome = translator.readFile(resource.getPath());
            log.info("Translation results: {}", dome.toString());
        } catch (Exception ex) {
            // assertTrue(false);
            ex.printStackTrace();
        }
        assertTrue(true);
    }

    @Test
    public void domeReadGeneratorTest() {
        resource = this.getClass().getResource("/dome_test_v1_5_multi.csv");
        try {
            Map dome = translator.readFile(resource.getPath());
            log.info("Translation results: {}", dome.toString());
        } catch (Exception ex) {
            assertTrue(false);
            ex.printStackTrace();
        }
        assertTrue(true);
    }

    @Test
    public void domeContinueTest() {
        resource = this.getClass().getResource("/continue_test.csv");
        try {
            Map dome = translator.readFile(resource.getPath());
            log.info("Translation results for continue_test: {}", dome.toString());
        } catch (Exception ex) {
            assertTrue(false);
            ex.printStackTrace();
        }
        assertTrue(true);
    }

    @Test
    public void domeLinkTest() {
        log.debug("dome link test start");
        resource = this.getClass().getResource("/Link.csv");
        try {
            Map dome = translator.readFile(resource.getPath());
            log.info("Translation results for link csv test: {}", dome.toString());
        } catch (Exception ex) {
            assertTrue(false);
            ex.printStackTrace();
        }
        assertTrue(true);
    }
}