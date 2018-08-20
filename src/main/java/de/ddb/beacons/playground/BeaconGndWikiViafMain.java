/* 
 * Copyright 2016-2018, Michael Büchner <m.buechner@dnb.de>
 * Deutsche Digitale Bibliothek
 * c/o Deutsche Nationalbibliothek
 * Informationsinfrastruktur
 * Adickesallee 1, D-60322 Frankfurt am Main 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ddb.beacons.playground;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

/**
 *
 * @author Michael Büchner
 */
public class BeaconGndWikiViafMain {

    // BEACON file name
    private static final String BEACON_FILENAME = "beacon_gndwikiviaf.txt";
    private static final String VIAF_FILENAME = "viaf-20150512-links.txt";
    private static final String[] BEACON_HEADER = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Wikipedia // Gemeinsame Normdatei (GND)",
        "#ISIL: WIKIDATA",
        "#COLLID: WIKIDATA",
        "#DESCRIPTION: This is a beacon file for GND UID to their image representation at Wikipedia generated from the VIAF Dump <http://viaf.org/viaf/data/>.",
        "#TIMESTAMP:" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date())
    };

    public BeaconGndWikiViafMain() {
    }

    public static void main(String[] args) throws IOException {
        new BeaconGndWikiViafMain().run();
    }

    private void flush(BufferedWriter bw, ViafEntity e, String type) throws IOException {
        String gnd = e.getLinks().get("DNB");
        String url = e.getLinks().get(type);
        if (gnd != null && url != null) {
            gnd = gnd.replaceFirst("^0+(?!$)", "");

            String base = url.substring(0, url.lastIndexOf('/') + 1);
            String last = url.substring(url.lastIndexOf('/') + 1, url.length());

            bw.write(gnd + "||" + base + URLEncoder.encode(last, "UTF-8"));
            bw.newLine();
        }
    }

    private void run() throws IOException {

        // sort input file
        File newInput = new File(VIAF_FILENAME + ".sorted");
        newInput.deleteOnExit();

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(BEACON_FILENAME), StandardCharsets.UTF_8))) {

            for (String s : BEACON_HEADER) {
                bw.write(s);
                bw.newLine();
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(newInput), "UTF8"))) {
                String line;
                ViafEntity e = new ViafEntity();
                while ((line = br.readLine()) != null) {
                    final String[] split = line.split("\t");
                    final String id = split[0];
                    final String[] kv = split[1].split("\\|");
                    if (e.getId() == null || !e.getId().equals(id)) {
                        flush(bw, e, "WKPDE");
                        e = new ViafEntity();
                        e.setId(id);
                    }
                    e.put(kv[0], kv[1]);
                }
            }
        }
    }
}

class ViafEntity {

    private String id;
    private TreeMap<String, String> links;

    public ViafEntity() {
        this.id = null;
        links = new TreeMap<>();
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    public boolean hasId() {
        return (id != null);
    }

    /**
     * @return the links
     */
    public TreeMap<String, String> getLinks() {
        return links;
    }

    /**
     * @param links the links to set
     */
    public void setLinks(TreeMap<String, String> links) {
        this.links = links;
    }

    public void put(String key, String value) {
        if (key.equals("WKP") && value.startsWith("Q")) {
            key = "WKPDATA";
        } else if (key.equals("WKP") && value.startsWith("http://")) {
            String lang = value.substring(value.indexOf("http://") + 7, value.indexOf(".wikipedia.org/wiki/"));
            key = "WKP" + lang.toUpperCase(Locale.GERMANY);
        }
        links.put(key, value);
    }
}
