package org.agmip.translators.csv;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Meng Zhang
 */
public class CSVOutputTest {
    
    private static final Logger log = LoggerFactory.getLogger(CSVOutputTest.class);
    private CSVOutput writer;
    private CSVInput reader;
    private URL resource = null;

    @Before
    public void setup() {
        writer = new CSVOutput();
        reader = new CSVInput();
    }

    @Test
    public void simpleWriterTest() throws Exception {
        resource = this.getClass().getResource("/Machakos_csv.zip");
        Map data = reader.readFile(resource.getPath());
        log.info("Translation results: {}", data.toString());
        writer.writeFile("output", data);
        ArrayList<File> outputs = writer.getOutputWthFiles();
        HashSet expecetedNames = new HashSet();
        expecetedNames.add("MK07.csv");
        expecetedNames.add("MK10.csv");
        expecetedNames.add("MK13.csv");
        expecetedNames.add("MK14.csv");
        assertTrue(expecetedNames.contains(outputs.get(0).getName()));
        assertTrue(expecetedNames.contains(outputs.get(1).getName()));
        assertTrue(expecetedNames.contains(outputs.get(2).getName()));
        assertTrue(expecetedNames.contains(outputs.get(3).getName()));
        assertTrue(outputs.get(0).delete());
        assertTrue(outputs.get(1).delete());
        assertTrue(outputs.get(2).delete());
        assertTrue(outputs.get(3).delete());
        new File("output").delete();
    }
}
