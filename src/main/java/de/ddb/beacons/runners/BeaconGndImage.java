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

import de.ddb.beacons.helpers.EntityFactsHelper;
import de.ddb.beacons.helpers.EntityFactsHelper.EntityType;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.LoggerFactory;

import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.Sites;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import de.ddb.beacons.helpers.ExampleHelpers;
import org.wikidata.wdtk.dumpfiles.DumpContentType;

public class BeaconGndImage implements EntityDocumentProcessor {

    // BEACON file name
    private final String beaconFilename = "{DUMPDATE}-beacon_gndimages.txt";
    private final String csvFilename = "{DUMPDATE}-beacon_gndimages.csv";
    private final String[] beaconHeader = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Deutsche Digitale Bibliothek <https://www.deutsche-digitale-bibliothek.de/>",
        "#ISIL: WIKIMEDIA",
        "#COLLID: WIKIMEDIA",
        "#DESCRIPTION: This is a concordance for GND URIs to the entity's image (P18) and logo (P154) at Wikimedia Commons. Made from Wikidata dump {DUMPDATE}.",
        "#TIMESTAMP: " + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
        "#FEED: " + "file:///{BEACONFILENAME}"
    };
    private final String[] csvHeader = {
        "GNDID;TYPE;IMAGE;LOGO;CREST"
    };
    // Crest (Wappen) property
    private final String crestProperty = "P94";
    // GND value property
    private final String gndProperty = "P227";
    // Image property
    private final String imageProperty = "P18";
    // Logo property
    private final String logoProperty = "P154";
    private final String imagePrefix = "Special:FilePath/";

    private BufferedWriter bw_beacon;
    private BufferedWriter bw_csv;
    private Sites sites;

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(BeaconGndImage.class);

    public void run() throws IOException {

        ExampleHelpers.configureLogging();

        // get site urls
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(ExampleHelpers.OFFLINE_MODE);

        // Download the sites table dump and extract information
        sites = dumpProcessingController.getSitesInformation();

        // get file name
        final String timestamp = dumpProcessingController.getWmfDumpFileManager().findMostRecentDump(DumpContentType.JSON).getDateStamp();
        final String localBeaconFilename = beaconFilename.replace("{DUMPDATE}", timestamp);
        this.bw_beacon = new BufferedWriter(new FileWriter(localBeaconFilename));
        this.bw_csv = new BufferedWriter(new FileWriter(csvFilename.replace("{DUMPDATE}", timestamp)));

        for (String s : beaconHeader) {
            s = s.replace("{DUMPDATE}", timestamp);
            s = s.replace("{BEACONFILENAME}", localBeaconFilename);
            bw_beacon.write(s);
            bw_beacon.newLine();
        }

        for (String s : csvHeader) {
            s = s.replace("{DUMPDATE}", timestamp);
            s = s.replace("{BEACONFILENAME}", localBeaconFilename);
            bw_csv.write(s);
            bw_csv.newLine();
        }

        EntityFactsHelper.get().load();
        ExampleHelpers.processEntitiesFromWikidataDump(this);
        EntityFactsHelper.get().save();

        bw_beacon.close();
        bw_csv.close();
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        String gnd = null;
        String image = null;
        String logo = null;
        String crest = null;

        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            final String propId = statementGroup.getProperty().getId();
            if (propId.equalsIgnoreCase(gndProperty)) {
                gnd = getStringValue(statementGroup);
                if (gnd == null || gnd.length() < 3) {
                    gnd = null;
                    continue;
                }
                gnd = gnd.substring(1, gnd.length() - 1);
            } else if (propId.equalsIgnoreCase(imageProperty)) {
                image = getStringValue(statementGroup);
                if (image == null || image.length() < 3) {
                    image = null;
                    continue;
                }

                image = image.substring(1, image.length() - 1);
                image = image.replace(" ", "_");
                image = sites.getPageUrl("commonswiki", imagePrefix + image);
            } else if (propId.equalsIgnoreCase(logoProperty)) {
                logo = getStringValue(statementGroup);
                if (logo == null || logo.length() < 3) {
                    logo = null;
                    continue;
                }

                logo = logo.substring(1, logo.length() - 1);
                logo = logo.replace(" ", "_");
                logo = sites.getPageUrl("commonswiki", imagePrefix + logo);
            } else if (propId.equalsIgnoreCase(crestProperty)) {
                crest = getStringValue(statementGroup);
                if (crest == null || crest.length() < 3) {
                    crest = null;
                    continue;
                }

                crest = crest.substring(1, crest.length() - 1);
                crest = crest.replace(" ", "_");
                crest = sites.getPageUrl("commonswiki", imagePrefix + crest);
            }
        }
        if ((gnd == null || gnd.isEmpty())) {
            return;
        }

        final EntityType entityType = EntityFactsHelper.get().getEntityType(gnd);

        try {
            // CSV
            final StringBuilder sb_csv = new StringBuilder();
            sb_csv.append(gnd)
                    .append(";")
                    .append(entityType.getEntityTypeDescription())
                    .append(";")
                    .append(image)
                    .append(";")
                    .append(logo)
                    .append(";")
                    .append(crest);
            bw_csv.write(sb_csv.toString());
            bw_csv.newLine();
            bw_csv.flush();

            // Beacon
            final StringBuilder sb_beacon = new StringBuilder();
            sb_beacon.append(gnd)
                    .append("||");
            //sb.append(entityType.getEntityTypeDescription());
            //sb.append("||");
            if (entityType == EntityType.ERROR) {
                return;
            } else if (entityType == EntityType.ORGANISATION && logo != null && !logo.isEmpty()) {
                sb_beacon.append(logo);
            } else if (entityType == EntityType.ORGANISATION && image != null && !image.isEmpty()) {
                sb_beacon.append(image);
            } else if (entityType == EntityType.PERSON && image != null && !image.isEmpty()) {
                sb_beacon.append(image);
            } else if (entityType == EntityType.PERSON && crest != null && !crest.isEmpty()) {
                // sb_beacon.append(crest);
                return;
            } else if (image != null && !image.isEmpty()) {
                sb_beacon.append(image);
            } else {
                return;
            }

            bw_beacon.write(sb_beacon.toString());
            bw_beacon.newLine();
            bw_beacon.flush();
        } catch (IOException ex) {
            logger.warn("Problem at GND Id " + gnd, ex);
        }
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
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
