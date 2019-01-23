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
package de.ddb.beacons.runners;

import de.ddb.beacons.helpers.Configuration;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
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

/**
 *
 * @author Michael Büchner
 */
public class BeaconGndWikipedia implements EntityDocumentProcessor {

    // BEACON file name
    private final static String[] BEACON_LANGS = {"dewiki", "enwiki", "dewikisource", "enwikisource"};
    private final static String BEACON_FILENAME = "{DUMPDATE}-beacon_{LANG}.txt";
    private final static Map<String, String> ISIL_CONCORDANCE = new HashMap<String, String>() {
        {
            put("dewikisource", "WIKISOURCE");
            put("enwiki", "WKP");
            put("dewiki", "WKPDE");
        }
    };
    private final static String[] BEACON_HEADER = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Deutsche Digitale Bibliothek <https://www.deutsche-digitale-bibliothek.de/>",
        "#ISIL: {LANG}",
        "#COLLID: {LANG}",
        "#DESCRIPTION: This is a condordance for GND URIs to \"{LANG}\". Made from Wikidata dump {DUMPDATE}.",
        "#TIMESTAMP: {DUMPDATE}",
        "#FEED: " + "file:///{BEACONFILENAME}"
    };

    // GND value property
    private final static String GND_PROP = "P227";

    private final Logger LOG = LoggerFactory.getLogger(BeaconGndWikipedia.class);

    private final Map<String, BufferedWriter> bws;
    private final Map<String, OutputStreamWriter> fws;
    private final Sites sites;

    public BeaconGndWikipedia(Sites sites, String timestamp) throws IOException {

        this.fws = new HashMap<>();
        this.bws = new HashMap<>();

        // Download the sites table dump and extract information
        this.sites = sites;

        // init files
        for (String lang : BEACON_LANGS) {

            // get file name
            String fname = BEACON_FILENAME.replace("{DUMPDATE}", timestamp.replaceAll("-", ""));
            fname = fname.replace("{LANG}", lang);
            final String fnameForHeader = fname;

            final OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(Configuration.get().getValue("destDir") + File.separator + fname), StandardCharsets.UTF_8);
            fws.put(lang, fw);
            final BufferedWriter bw = new BufferedWriter(fw);
            bws.put(lang, bw);

            // write header
            for (String s : BEACON_HEADER) {
                // we have different ISIL than Wikidata
                if (ISIL_CONCORDANCE.containsKey(lang)) {
                    s = s.replaceAll("\\{DUMPDATE\\}", timestamp).replace("{LANG}", ISIL_CONCORDANCE.get(lang));
                } else {
                    s = s.replaceAll("\\{DUMPDATE\\}", timestamp).replace("{LANG}", lang);
                }
                s = s.replaceAll("\\{BEACONFILENAME\\}", fnameForHeader);
                bw.write(s);
                bw.newLine();
            }
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        String gnd = null;
        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            if (statementGroup.getProperty().getId().equals(GND_PROP)) {
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

        for (String lang : BEACON_LANGS) {
            String link;
            try {
                link = ((SiteLink) itemDocument.getSiteLinks().get(lang)).getPageTitle();
                link = sites.getPageUrl(lang, link);
                link = link.replaceFirst("http:", "");
                if (link.length() > 0) {
                    final BufferedWriter bw = bws.get(lang);
                    bw.write(gnd + "||" + link);
                    bw.newLine();
                }
            } catch (NullPointerException ex) {
                // do nothing (there's no site url)
            } catch (IOException ex) {
                LOG.warn("Could not write to file {}. {}", BEACON_FILENAME, ex.getLocalizedMessage());
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

    public void close() {
        // close files
        for (String lang : BEACON_LANGS) {
            try {
                bws.get(lang).close();
            } catch (IOException e) {
                //nothing
            }
            try {
                fws.get(lang).close();
            } catch (IOException e) {
                //nothing
            }
        }
    }
}
