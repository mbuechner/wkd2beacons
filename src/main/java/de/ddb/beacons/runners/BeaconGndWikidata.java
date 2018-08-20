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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentProcessor;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;

/**
 *
 * @author Michael Büchner
 */
public class BeaconGndWikidata implements EntityDocumentProcessor {

    // BEACON file name
    private final static String BEACON_FILENAME = "{DUMPDATE}-beacon_wikidata.txt";
    private final static String[] BEACON_HEADER = {
        "#FORMAT: BEACON",
        "#PREFIX: http://d-nb.info/gnd/",
        "#CONTACT: Michael Büchner <m.buechner@dnb.de>",
        "#INSTITUTION: Deutsche Digitale Bibliothek <https://www.deutsche-digitale-bibliothek.de/>",
        "#ISIL: WIKIDATA",
        "#COLLID: WIKIDATA",
        "#DESCRIPTION: This is a concordance for GND URIs to Wikidata data records. Made from Wikidata dump {DUMPDATE}.",
        "#TIMESTAMP: " + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
        "#FEED: " + "file:///{BEACONFILENAME}"
    };

    private final static Logger LOGGER = LoggerFactory.getLogger(BeaconGndWikidata.class);

    // GND value property
    private final static String GND_PROP = "P227";
    private final BufferedWriter bw;

    public BeaconGndWikidata(String timestamp) throws IOException {

        final String fname = BEACON_FILENAME.replace("{DUMPDATE}", timestamp);

        this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ConfigurationHelper.get().getValue("destDir") + File.separator + fname), StandardCharsets.UTF_8));

        for (String s : BEACON_HEADER) {
            s = s.replace("{DUMPDATE}", timestamp);
            s = s.replace("{BEACONFILENAME}", fname);
            bw.write(s);
            bw.newLine();
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        String gnd = null;

        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            final String propId = statementGroup.getProperty().getId();
            if (propId.equalsIgnoreCase(GND_PROP)) {
                gnd = getStringValue(statementGroup);
                if (gnd == null || gnd.length() < 3) {
                    gnd = null;
                    continue;
                }
                gnd = gnd.substring(1, gnd.length() - 1);
                break;
            }
        }
        if (gnd != null && gnd.length() > 0) {
            try {
                final String s = gnd + "||https://www.wikidata.org/wiki/" + itemDocument.getItemId().getId();
                bw.write(s);
                bw.newLine();
            } catch (IOException ex) {
                LOGGER.warn("Could not write to file " + BEACON_FILENAME, ex);
            }
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

    public void close() {
        try {
            bw.close();
        } catch (IOException e) {
            //nothing
        }
    }
}
