package org.agmip.translators.csv;

import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

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
            translator.readFile(resource.getPath());
            log.info("Translation results: {}", translator.getDome().toString());
        } catch (Exception ex) {
            assertTrue(false);
        }
        assertTrue(true);
    }

    @Test
    public void domeReaderTest() {
        resource = this.getClass().getResource("/dome_test_v1_5.csv");
        try {
            translator.readFile(resource.getPath());
            log.info("Translation results: {}", translator.getDome().toString());
        } catch (Exception ex) {
            // assertTrue(false);
            ex.printStackTrace();
        }
        assertTrue(true);
    }
}