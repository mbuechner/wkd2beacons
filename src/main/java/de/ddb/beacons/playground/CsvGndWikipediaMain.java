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

import de.ddb.beacons.helpers.EntityTimerProcessor;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.text.StringEscapeUtils;
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
import org.slf4j.Logger;

/**
 *
 * @author Michael Büchner
 */
public class CsvGndWikipediaMain implements EntityDocumentProcessor {

    // BEACON file name
    private static final String[] BEACON_LANGS = {"dewiki", "enwiki"};
    private static final String BEACON_FILENAME = "csv_{LANG}.txt";
    private static final char SEPARATOR = ';';
    // GND value property
    private static final String GND_PROPERTY = "P227";

    private final BufferedWriter bw;
    private final Sites sites;

    private static enum DumpProcessingMode {
        JSON, CURRENT_REVS, ALL_REVS, CURRENT_REVS_WITH_DAILIES, ALL_REVS_WITH_DAILIES, JUST_ONE_DAILY_FOR_TEST
    }
    private final static DumpProcessingMode DUMP_FILE_MODE = DumpProcessingMode.JSON;
    private final static int TIMEOUT_SEC = 0;

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvGndWikipediaMain.class);

    public static void main(String[] args) throws IOException {

        // init file
        final StringBuffer sb = new StringBuffer();
        for (String lang : BEACON_LANGS) {
            sb.append(lang);
            sb.append('_');
        }
        final String fname = BEACON_FILENAME.replace("{LANG}", sb.toString().substring(0, sb.length() - 1));

        // get site urls
        try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fname), StandardCharsets.UTF_8))) {
            // get site urls
            DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
            dumpProcessingController.setOfflineMode(false);
            // Download the sites table dump and extract information
            Sites sites = dumpProcessingController.getSitesInformation();
            CsvGndWikipediaMain processor = new CsvGndWikipediaMain(bw, sites);
            processEntitiesFromWikidataDump(dumpProcessingController, processor);
            processor.printStatus();
            // close file
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
            if (statementGroup.getProperty().getId().equals(GND_PROPERTY)) {
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

        final StringBuffer sb = new StringBuffer();
        for (String lang : BEACON_LANGS) {

            String link = null;
            try {
                link = ((SiteLink) itemDocument.getSiteLinks().get(lang)).getPageTitle();
                link = sites.getPageUrl(lang, link);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                // no sitelink availible
            }

            if (link != null && !link.isEmpty()) {
                sb.append(link);
            }
            sb.append(SEPARATOR);
        }

        if (sb.length() <= BEACON_LANGS.length) {
            return; // no links at all because there're only separators
        }

        final String out = StringUtils.stripEnd(sb.toString(), String.valueOf(SEPARATOR));

        try {
            bw.write(gnd);
            bw.write(SEPARATOR);
            bw.write(out);
            bw.newLine();
        } catch (IOException ex) {
            LOGGER.warn("Could not write to file " + BEACON_FILENAME, ex);
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

    public static void processEntitiesFromWikidataDump(DumpProcessingController dumpProcessingController, EntityDocumentProcessor entityDocumentProcessor) throws IOException {

        // Should we process historic revisions or only current ones?
        boolean onlyCurrentRevisions;
        switch (DUMP_FILE_MODE) {
            case ALL_REVS:
            case ALL_REVS_WITH_DAILIES:
                onlyCurrentRevisions = false;
                break;
            case CURRENT_REVS:
            case CURRENT_REVS_WITH_DAILIES:
            case JSON:
            case JUST_ONE_DAILY_FOR_TEST:
            default:
                onlyCurrentRevisions = true;
        }

        // Subscribe to the most recent entity documents of type wikibase item:
        dumpProcessingController.registerEntityDocumentProcessor(entityDocumentProcessor, null, onlyCurrentRevisions);

        // Also add a timer that reports some basic progress information:
        EntityTimerProcessor entityTimerProcessor = new EntityTimerProcessor(TIMEOUT_SEC);
        dumpProcessingController.registerEntityDocumentProcessor(entityTimerProcessor, null, onlyCurrentRevisions);

        try {
            // Start processing (may trigger downloads where needed):
            switch (DUMP_FILE_MODE) {
                case ALL_REVS:
                case CURRENT_REVS:
                    dumpProcessingController.processMostRecentMainDump();
                    break;
                case ALL_REVS_WITH_DAILIES:
                case CURRENT_REVS_WITH_DAILIES:
                    dumpProcessingController.processAllRecentRevisionDumps();
                    break;
                case JSON:
                    dumpProcessingController.processMostRecentJsonDump();
                    break;
                case JUST_ONE_DAILY_FOR_TEST:
                    dumpProcessingController.processMostRecentMainDump();
                    break;
                default:
                    throw new RuntimeException("Unsupported dump processing type " + DUMP_FILE_MODE);
            }
        } catch (EntityTimerProcessor.TimeoutException e) {
            // The timer caused a time out. Continue and finish normally.
        } catch (RuntimeException e) {
            LOGGER.error("Error processing data dump", e);
        }

        // Print final timer results:
        entityTimerProcessor.stop();
    }
}
