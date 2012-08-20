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
public class ZipFileTest {
    private static Logger LOG = LoggerFactory.getLogger(ZipFileTest.class);
    private CSVInput importer;
    private URL zipTest;

    @Before
    public void setup() {
        importer = new CSVInput();
        zipTest      = this.getClass().getResource("/test.zip");
    }

    @Test
    public void zipFileTest() {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(zipTest.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Zip File Test results: "+result.toString());
    }
}
