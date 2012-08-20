package org.agmip.translators.csv;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
public class LiveFireTest {
    private static Logger LOG = LoggerFactory.getLogger(LiveFireTest.class);
    private CSVInput importer;
    private URL machakos, wheat;

    @Before
    public void setup() {
        importer = new CSVInput();
        machakos = this.getClass().getResource("/machakos.csv");
        wheat    = this.getClass().getResource("/wheat.zip");
    }

    @Test
    public void machakosTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(machakos.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Machakos results: "+result.toString());
    }

    @Test
    public void wheatTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(wheat.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Wheat pilot results: "+result.toString());
    }
}
