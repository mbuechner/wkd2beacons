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
package de.ddb.beacons;

import de.ddb.beacons.helpers.Configuration;
import de.ddb.beacons.helpers.EntityFacts;
import de.ddb.beacons.helpers.EntityTimerProcessor;
import de.ddb.beacons.runners.BeaconGndImage;
import de.ddb.beacons.runners.BeaconGndWikidata;
import de.ddb.beacons.runners.BeaconGndWikipedia;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessorBroker;
import org.wikidata.wdtk.datamodel.interfaces.Sites;
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;

/**
 *
 * @author Michael Büchner, Markus Kroetzsch
 */
public class App {

    private static enum DumpProcessingMode {
        JSON, CURRENT_REVS, ALL_REVS, CURRENT_REVS_WITH_DAILIES, ALL_REVS_WITH_DAILIES, JUST_ONE_DAILY_FOR_TEST
    }
    private final static DumpProcessingMode DUMP_FILE_MODE = DumpProcessingMode.JSON;

    private final static int TIMEOUT_SEC = 0;
    private final static Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws IOException {

        final Options options = new Options();
        options.addOption("d", true, "Folder to stored all downloaded Wikidata dumps and entity type database (default: data/)");
        options.addOption("o", true, "Destination folder (default: beacons/)");
        options.addOption("h", false, "Print help text");
        options.addOption("v", false, "Print version");

        try {
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("d")) {
                Configuration.get().setValue("dataDir", cmd.getOptionValue("d"));
            }

            if (cmd.hasOption("o")) {
                Configuration.get().setValue("destDir", cmd.getOptionValue("o"));
            }

            if (cmd.hasOption("h")) {
                final HelpFormatter help = new HelpFormatter();
                help.printHelp("java -Dlog.file=wkd2beacons.log -jar wkd2beacons.jar", options, true);
                return;
            } else if (cmd.hasOption("v")) {
                System.out.println("Version 1.2");
                return;
            }
        } catch (ParseException e) {
            LOG.error(e.getLocalizedMessage());
        }

        final App app = new App();
        app.run();
    }

    private void run() throws IOException {

        final long start = System.currentTimeMillis();
        final File destDir = new File(Configuration.get().getValue("destDir"));

        boolean folderExisted = destDir.exists() || destDir.mkdirs();
        if (!folderExisted) {
            LOG.error("Could not create directory {}", Configuration.get().getValue("destDir"));
            return;
        }

        // load EF database (if exist)
        EntityFacts.get().load();

        // get site urls
        final DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(false);

        // Use another download directory:
        dumpProcessingController.setDownloadDirectory(Configuration.get().getValue("dataDir"));

        // Download the sites table dump and extract information
        final Sites sites = dumpProcessingController.getSitesInformation();

        // get timestamp and format it as ISO
        String timestamp = dumpProcessingController.getWmfDumpFileManager().findMostRecentDump(DumpContentType.JSON).getDateStamp();

        final SimpleDateFormat parser = new SimpleDateFormat("yyyyMMdd");
        Date date = null;
        try {
            date = parser.parse(timestamp);
        } catch (java.text.ParseException ex) {
            // nothing
        }

        if (date != null) {
            final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            timestamp = formatter.format(date);
        }

        final BeaconGndImage bgi = new BeaconGndImage(sites, timestamp);
        final BeaconGndWikidata bgwd = new BeaconGndWikidata(timestamp);
        final BeaconGndWikipedia bgwp = new BeaconGndWikipedia(sites, timestamp);

        final EntityDocumentProcessorBroker edpb = new EntityDocumentProcessorBroker();
        edpb.registerEntityDocumentProcessor(bgi);
        edpb.registerEntityDocumentProcessor(bgwd);
        edpb.registerEntityDocumentProcessor(bgwp);

        // run that shit! ;)
        processEntitiesFromWikidataDump(dumpProcessingController, edpb);

        // shutdown
        EntityFacts.get().save();
        bgi.close();
        bgwd.close();
        bgwp.close();

        final long duration = System.currentTimeMillis() - start;

        LOG.info(String.format("Done. That took %02d:%02d:%02d hour(s).",
                TimeUnit.MILLISECONDS.toHours(duration),
                TimeUnit.MILLISECONDS.toMinutes(duration) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(duration) % TimeUnit.MINUTES.toSeconds(1)
        ));
    }

    /**
     * Processes all entities in a Wikidata dump using the given entity
     * processor. By default, the most recent JSON dump will be used. In offline
     * mode, only the most recent previously downloaded file is considered.
     *
     * @param dumpProcessingController
     * @param entityDocumentProcessor the object to use for processing entities
     * in this dump
     */
    private void processEntitiesFromWikidataDump(DumpProcessingController dumpProcessingController, EntityDocumentProcessor entityDocumentProcessor) {

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
                    dumpProcessingController.processMostRecentDailyDump();
                    break;
                default:
                    throw new RuntimeException("Unsupported dump processing type " + DUMP_FILE_MODE);
            }
        } catch (EntityTimerProcessor.TimeoutException e) {
            // The timer caused a time out. Continue and finish normally.
        } catch (RuntimeException e) {
            LOG.error("Error processing data dump", e);
        }

        // Print final timer results:
        entityTimerProcessor.stop();
    }
}
