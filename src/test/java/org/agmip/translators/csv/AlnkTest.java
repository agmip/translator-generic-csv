package org.agmip.translators.csv;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
//import java.util.HashMap;
import java.util.Map;
import org.agmip.util.MapUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlnkTest {
    private static final Logger log = LoggerFactory.getLogger(AlnkTest.class);
    private AlnkInput reader;
    private AlnkOutput writer;
    private URL resource = null;

    @Before
    public void setup() {
        reader = new AlnkInput();
        writer = new AlnkOutput();
    }

    @Test
    public void ACOMTest() {
        log.info("Test for reading link information from ACMO file starts");
        resource = this.getClass().getResource("/ACMO.csv");
        try {
            Map links = reader.readFile(resource.getPath());
            log.info("Translation results for link csv test: {}", links.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
        assertTrue(true);
    }

    @Test
    public void ALNKTest() {
        log.info("Test for reading link information from ALNK file starts");
        resource = this.getClass().getResource("/Linkage.alnk");
        try {
            Map links = reader.readFile(resource.getPath());
            writer.writeFile("output/Linkage.alnk", toACEFormat(links));
            log.info("Translation results for link csv test: {}", links.toString());
            File alnk = writer.getAlnkFile();
            alnk.delete();
            alnk.getParentFile().delete();
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
        assertTrue(true);
    }
    
    private Map toACEFormat(Map links) {
        HashMap ret = new HashMap();
        ArrayList<HashMap> arr = new ArrayList();
        ret.put("experiments", arr);
        HashMap<String, String> ovlLinks = MapUtil.getObjectOr(links, "link_overlay", new HashMap());
        HashMap<String, String> stgLinks = MapUtil.getObjectOr(links, "link_stragty", new HashMap());
        for (String key : ovlLinks.keySet()) {
            HashMap m = new HashMap();
            m.put("exname", key.substring(7));
            m.put("field_overlay", ovlLinks.get(key));
            m.put("seasonal_strategy", stgLinks.get(key));
            arr.add(m);
        }
        return ret;
    }
}