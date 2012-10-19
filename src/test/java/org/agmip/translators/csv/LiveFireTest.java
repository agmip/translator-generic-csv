package org.agmip.translators.csv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.Before;
//import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.agmip.util.JSONAdapter;

/**
 * Unit test for simple App.
 */
public class LiveFireTest {
    private static Logger LOG = LoggerFactory.getLogger(LiveFireTest.class);
    private CSVInput importer;
    //private URL machakos, wheat, machakosOut;
    private URL wheatField;

    @Before
    public void setup() {
        importer = new CSVInput();
        //machakos = this.getClass().getResource("/Machakos_csv.zip");
        //wheat    = this.getClass().getResource("/wheat.zip");
        wheatField = this.getClass().getResource("/field.csv");
    }
   
    /*
    @Test
    public void machakosTest() throws IOException {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(machakos.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Machakos JSON: "+JSONAdapter.toJSON(result));
        File out = new File("machakos.json");
        if (! out.exists()) {
            out.createNewFile();
        }
        FileWriter fw = new FileWriter(out.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(JSONAdapter.toJSON(result));
        bw.close();
        fw.close();
    }
*/
    @Test
    public void wheatTest() throws IOException {
        Map result = new LinkedHashMap();
        try {
            result = importer.readFile(wheatField.getPath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LOG.info("Wheat Pilot JSON: "+JSONAdapter.toJSON(result));
    }
}
