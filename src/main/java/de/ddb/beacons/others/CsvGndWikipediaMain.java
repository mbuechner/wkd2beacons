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
import de.ddb.beacons.runners.BeaconGndWikipedia;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
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

public class CsvGndWikipediaMain implements EntityDocumentProcessor {

    // BEACON file name
    static final String[] beaconLanguages = {"dewiki", "enwiki"};
    static final String beaconFilename = "csv_{LANG}.txt";
    static final char separator = ';';
    static final boolean sortBeacon = true;
    // GND value property
    static final String gndProperty = "P227";

    BufferedWriter bw;
    Sites sites;

    static final org.slf4j.Logger logger = LoggerFactory.getLogger(BeaconGndWikipedia.class);

    public static void main(String[] args) throws IOException {

        ExampleHelpers.configureLogging();

        // init file
        String fname = "";
        for (String lang : beaconLanguages) {
            fname += lang + "_";
        }
        fname = beaconFilename.replace("{LANG}", fname.substring(0, fname.length() - 1));
        fname = sortBeacon ? fname + ".unsorted" : fname;

        FileWriter fw = new FileWriter(fname);
        BufferedWriter bw = new BufferedWriter(fw);

        // get site urls
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(ExampleHelpers.OFFLINE_MODE);
        // Download the sites table dump and extract information
        Sites sites = dumpProcessingController.getSitesInformation();

        CsvGndWikipediaMain processor = new CsvGndWikipediaMain(bw, sites);
        ExampleHelpers.processEntitiesFromWikidataDump(processor);
        processor.printStatus();

        // close file   
        bw.close();

        if (sortBeacon) {
            // sorting files
            // unsorted file
            final File uf = new File(fname);

            // tmp file for sorted results
            final File sf = new File(fname.replace(".unsorted", ""));
            sf.createNewFile();

            final Comparator<String> com = new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    if (s1.startsWith("#") || s2.startsWith("#")) {
                        return 0;
                    }
                    final Integer i1 = Integer.parseInt(s1.substring(0, s1.indexOf(separator)).replace("X", "9").replace("-", ""));
                    final Integer i2 = Integer.parseInt(s2.substring(0, s2.indexOf(separator)).replace("X", "9").replace("-", ""));
                    return i1.compareTo(i2);
                }
            };

            // sort that shit
            ExternalSort.mergeSortedFiles(ExternalSort.sortInBatch(uf, com), sf);

            // delete existing file
            uf.delete();
        }
    }

    CsvGndWikipediaMain(BufferedWriter bw, Sites sites) {
        this.bw = bw;
        this.sites = sites;
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

        if (gnd == null || gnd.isEmpty()) {
            return; // we dont have a GND id
        }

        String out = "";
        for (String lang : beaconLanguages) {

            String link = null;
            try {
                link = ((SiteLink) itemDocument.getSiteLinks().get(lang)).getPageTitle();
                link = sites.getPageUrl(lang, link);
            } catch (Exception e) {
                // no sitelink availible
            }

            if (link != null && !link.isEmpty()) {
                out += link;
            }
            out += separator;
        }

        if (out.length() <= beaconLanguages.length) {
            return; // no links at all because there're only separators
        }

        out = StringUtils.stripEnd(out, String.valueOf(separator));

        try {
            bw.write(gnd);
            bw.write(separator);
            bw.write(out);
            bw.newLine();
        } catch (IOException ex) {
            logger.warn("Could not write to file " + beaconFilename, ex);
        }

    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        // Nothing to do
    }

    /**
     * Prints the current status, time and entity count.
     */
    public void printStatus() {

    }

    /**
     * Prints some basic documentation about this program.
     */
    public static void printDocumentation() {

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
