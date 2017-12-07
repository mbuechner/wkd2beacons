/* 
 * Copyright 2016, Michael Büchner <m.buechner@dnb.de>
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
package de.ddb.beacons.others;

import com.google.code.externalsorting.ExternalSort;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.TreeMap;

public class BeaconGndWikiViaf {

    // BEACON file name
    static final String beaconFilename = "beacon_gndwikiviaf.txt";
    static final String viafFilename = "d:\\Downloads\\VIAF\\viaf-20150512-links.txt";
    static final String[] beaconLanguages = {"WKPEN", "WKPDE", "WKPDATA"};
    static final boolean sortBeacon = true;
    static final String[] beaconHeader = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Wikipedia // Gemeinsame Normdatei (GND)",
        "#ISIL: WIKIDATA",
        "#COLLID: WIKIDATA",
        "#DESCRIPTION: This is a beacon file for GND UID to their image representation at Wikipedia generated from the VIAF Dump <http://viaf.org/viaf/data/>.",
        "#TIMESTAMP:" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date())
    };

    public BeaconGndWikiViaf() {
    }

    public static void main(String[] args) throws IOException {
        new BeaconGndWikiViaf().run();
    }

    private void flush(BufferedWriter bw, ViafEntity e, String type) throws IOException {
        String gnd = e.getLinks().get("DNB");
        String url = e.getLinks().get(type);
        if (gnd != null && url != null) {
            gnd = gnd.replaceFirst("^0+(?!$)", "");
            
            String base = url.substring(0, url.lastIndexOf("/") + 1);
            String last = url.substring(url.lastIndexOf("/") + 1, url.length());

            bw.write(gnd + "||" + base + URLEncoder.encode(last, "UTF-8"));
            bw.newLine();
        }
    }

    private void run() throws IOException {
        
        // sort input file
        File newInput = new File(viafFilename + ".sorted");
        newInput.deleteOnExit();
        ExternalSort.sort(new File(viafFilename), newInput);
        
        final String fname = sortBeacon ? beaconFilename + ".unsorted" : beaconFilename;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fname))) {

            for (String s : beaconHeader) {
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

        if (sortBeacon) {
            // unsorted file
            final File uf = new File(fname);

            // sorted file
            final File sf = new File(fname.replace(".unsorted", ""));
            sf.createNewFile();

            final Comparator<String> com = new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    if (s1.startsWith("#") || s2.startsWith("#")) {
                        return 0;
                    }
                    final Integer i1 = Integer.parseInt(s1.substring(0, s1.indexOf("|")).replace("X", "9").replace("-", ""));
                    final Integer i2 = Integer.parseInt(s2.substring(0, s2.indexOf("|")).replace("X", "9").replace("-", ""));
                    return i1.compareTo(i2);
                }
            };

            // sort that shit
            ExternalSort.mergeSortedFiles(ExternalSort.sortInBatch(uf, com), sf);

            // delete existing unsorted file
            uf.delete();
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
            key = "WKP" + lang.toUpperCase();
        }
        links.put(key, value);
    }
}
