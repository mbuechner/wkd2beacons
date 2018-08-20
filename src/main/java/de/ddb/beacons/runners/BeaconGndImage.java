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

import de.ddb.beacons.helpers.ConfigurationHelper;
import de.ddb.beacons.helpers.EntityFactsHelpers;
import de.ddb.beacons.helpers.EntityFactsHelpers.EntityType;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
import org.slf4j.Logger;

/**
 *
 * @author Michael Büchner
 */
public class BeaconGndImage implements EntityDocumentProcessor {

    // BEACON file name
    private final static String BEACON_FILENAME = "{DUMPDATE}-beacon_gndimages.txt";
    private final static String CSV_FILENAME = "{DUMPDATE}-beacon_gndimages.csv";
    private final static String[] BEACON_HEADER = {
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
    private final static String[] CSV_HEADER = {
        "GNDID;TYPE;IMAGE;LOGO;CREST"
    };
    // Crest (Wappen) property
    private final static String CREST_PROP = "P94";
    // GND value property
    private final static String GND_PROP = "P227";
    // Image property
    private final static String IMAGE_PROP = "P18";
    // Logo property
    private final static String LOGO_PROP = "P154";
    private final static String IMAGE_PREFIX = "Special:FilePath/";

    private final static Logger LOGGER = LoggerFactory.getLogger(BeaconGndImage.class);

    private final BufferedWriter bw_beacon;
    private final BufferedWriter bw_csv;
    private final Sites sites;

    public BeaconGndImage(Sites sites, String timestamp) throws IOException {

        this.sites = sites;
        final String localBeaconFilename = BEACON_FILENAME.replace("{DUMPDATE}", timestamp);

        this.bw_beacon = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ConfigurationHelper.get().getValue("destDir") + File.separator + localBeaconFilename), StandardCharsets.UTF_8));
        this.bw_csv = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ConfigurationHelper.get().getValue("destDir") + File.separator + CSV_FILENAME.replace("{DUMPDATE}", timestamp)), StandardCharsets.UTF_8));

        for (String s : BEACON_HEADER) {
            s = s.replace("{DUMPDATE}", timestamp);
            s = s.replace("{BEACONFILENAME}", localBeaconFilename);
            bw_beacon.write(s);
            bw_beacon.newLine();
        }

        for (String s : CSV_HEADER) {
            s = s.replace("{DUMPDATE}", timestamp);
            s = s.replace("{BEACONFILENAME}", localBeaconFilename);
            bw_csv.write(s);
            bw_csv.newLine();
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        String gnd = null;
        String image = null;
        String logo = null;
        String crest = null;

        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            final String propId = statementGroup.getProperty().getId();
            if (propId.equalsIgnoreCase(GND_PROP)) {
                gnd = getStringValue(statementGroup);
                if (gnd == null || gnd.length() < 3) {
                    gnd = null;
                    continue;
                }
                gnd = gnd.substring(1, gnd.length() - 1);
            } else if (propId.equalsIgnoreCase(IMAGE_PROP)) {
                image = getStringValue(statementGroup);
                if (image == null || image.length() < 3) {
                    image = null;
                    continue;
                }

                image = image.substring(1, image.length() - 1);
                image = image.replace(" ", "_");
                image = sites.getPageUrl("commonswiki", IMAGE_PREFIX + image);
            } else if (propId.equalsIgnoreCase(LOGO_PROP)) {
                logo = getStringValue(statementGroup);
                if (logo == null || logo.length() < 3) {
                    logo = null;
                    continue;
                }

                logo = logo.substring(1, logo.length() - 1);
                logo = logo.replace(" ", "_");
                logo = sites.getPageUrl("commonswiki", IMAGE_PREFIX + logo);
            } else if (propId.equalsIgnoreCase(CREST_PROP)) {
                crest = getStringValue(statementGroup);
                if (crest == null || crest.length() < 3) {
                    crest = null;
                    continue;
                }

                crest = crest.substring(1, crest.length() - 1);
                crest = crest.replace(" ", "_");
                crest = sites.getPageUrl("commonswiki", IMAGE_PREFIX + crest);
            }
        }
        if ((gnd == null || gnd.isEmpty())) {
            return;
        }

        final EntityType entityType = EntityFactsHelpers.get().getEntityType(gnd);

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
            if (entityType == EntityType.NA) {
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
            LOGGER.warn("Problem at GND Id {}", gnd, ex);
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

    public void close() throws IOException {

        try {
            bw_beacon.close();
        } catch (IOException e) {
            //nothing
        }

        try {
            bw_csv.close();
        } catch (IOException e) {
            //nothing
        }
    }
}
