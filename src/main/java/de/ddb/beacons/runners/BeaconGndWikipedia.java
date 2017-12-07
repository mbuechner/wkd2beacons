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
package de.ddb.beacons.runners;

import com.google.code.externalsorting.ExternalSort;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.LoggerFactory;

import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.SiteLink;
import org.wikidata.wdtk.datamodel.interfaces.Sites;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import de.ddb.beacons.helpers.ExampleHelpers;
import org.wikidata.wdtk.dumpfiles.DumpContentType;

public class BeaconGndWikipedia implements EntityDocumentProcessor {

    // BEACON file name
    private final String[] beaconLanguages = {"dewiki", "enwiki", "dewikisource", "enwikisource"};
    private final String beaconFilename = "{DUMPDATE}-beacon_{LANG}.txt";
    private final Map<String, String> correctIsil = new HashMap<String, String>() {
        {
            put("dewikisource", "WIKISOURCE");
            put("enwiki", "WKP");
            put("dewiki", "WKPDE");
        }
    };
    private final boolean sortBeacon = false;
    private final String[] beaconHeader = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Deutsche Digitale Bibliothek <https://www.deutsche-digitale-bibliothek.de/>",
        "#ISIL: {LANG}",
        "#COLLID: {LANG}",
        "#DESCRIPTION: This is a condordance for GND URIs to \"{LANG}\". Made from Wikidata dump {DUMPDATE}.",
        "#TIMESTAMP: " + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
        "#FEED: " + "file:///{BEACONFILENAME}"
    };

    // GND value property
    private final String gndProperty = "P227";

    private Map<String, BufferedWriter> bws;
    private Sites sites;

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(BeaconGndWikipedia.class);

    public void run() throws IOException {

        ExampleHelpers.configureLogging();

        Map<String, FileWriter> fws = new HashMap<>();
        bws = new HashMap<>();

        // get site urls
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(ExampleHelpers.OFFLINE_MODE);

        // Download the sites table dump and extract information
        sites = dumpProcessingController.getSitesInformation();

        final String timestamp = dumpProcessingController.getWmfDumpFileManager().findMostRecentDump(DumpContentType.JSON).getDateStamp();

        // init files
        for (String lang : beaconLanguages) {

            // get file name
            String fname = beaconFilename.replace("{DUMPDATE}", timestamp);
            fname = fname.replace("{LANG}", lang);
            final String fnameForHeader = fname;
            fname = sortBeacon ? fname + ".unsorted" : fname;

            FileWriter fw = new FileWriter(fname);
            fws.put(lang, fw);
            BufferedWriter bw = new BufferedWriter(fw);
            bws.put(lang, bw);

            // write header
            for (String s : beaconHeader) {
                // we have different ISIL than Wikidata
                if (correctIsil.containsKey(lang)) {
                    s = s.replace("{DUMPDATE}", timestamp).replace("{LANG}", correctIsil.get(lang));
                } else {
                    s = s.replace("{DUMPDATE}", timestamp).replace("{LANG}", lang);
                }
                s = s.replace("{BEACONFILENAME}", fnameForHeader);
                bw.write(s);
                bw.newLine();
            }
        }

        ExampleHelpers.processEntitiesFromWikidataDump(this);

        // close files
        for (String lang : beaconLanguages) {
            BufferedWriter bw = bws.get(lang);
            bw.close();
            FileWriter fw = fws.get(lang);
            fw.close();
        }

        if (sortBeacon) {
            // sorting files
            for (String lang : beaconLanguages) {
                final String fname = beaconFilename.replace("{LANG}", lang).replace("{DUMPDATE}", timestamp);

                // unsorted file
                final File uf = new File(fname + ".unsorted");

                // file for sorted results
                final File sf = new File(fname);
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

                // delete existing file
                uf.delete();
            }
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        String gnd = null;
        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            if (statementGroup.getProperty().getId().equals(gndProperty)) {
                gnd = getStringValue(statementGroup);
                if (gnd == null || gnd.length() < 3) {
                    gnd = null;
                } else {
                    gnd = gnd.substring(1, gnd.length() - 1);
                }
                break; // the for-loop
            }
        }

        if (gnd == null || gnd.length() <= 0) {
            return; // we dont have an GND id
        }

        for (String lang : beaconLanguages) {
            String link;
            try {
                link = ((SiteLink) itemDocument.getSiteLinks().get(lang)).getPageTitle();
                link = sites.getPageUrl(lang, link);
                link = link.replaceFirst("http:", "");
            } catch (Exception e) {
                continue; // no sitelink availible
            }

            if (link != null && link.length() > 0) {
                try {
                    BufferedWriter bw = bws.get(lang);
                    bw.write(gnd + "||" + link);
                    bw.newLine();
                } catch (IOException ex) {
                    logger.warn("Could not write to file " + beaconFilename, ex);
                }
            }
        }
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        // Nothing to do
    }

    private String getStringValue(StatementGroup statementGroup) {
        for (Statement s : statementGroup.getStatements()) {
            if (s.getClaim().getMainSnak() instanceof ValueSnak) {
                Value v = ((ValueSnak) s.getClaim().getMainSnak()).getValue();
                return StringEscapeUtils.unescapeJson(v.toString());
            }
        }
        return null;
    }
}
