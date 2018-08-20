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

import de.ddb.beacons.App;
import de.ddb.beacons.helpers.EntityFactsHelpers;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;

/**
 *
 * @author Michael Büchner
 */
public class FindDuplicatesMain implements EntityDocumentProcessor {

    // first propertiy
    private final String PROP01 = "P18";
    // second property
    // private final String PROP02 = "P94";
    // private final String PROP02 = "P1801";
    private final String PROP02 = "P1442";

    private final String outputFilename = "{DUMPDATE}-" + PROP01 + "-to-" + PROP02 + ".txt";

    private final Logger LOG = LoggerFactory.getLogger(FindDuplicatesMain.class);
    private BufferedWriter outputFile;

    public static void main(String[] args) throws IOException {
        new FindDuplicatesMain().run();
    }

    private void run() throws IOException {

        // get site urls
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(false);

        final String timestamp = dumpProcessingController.getWmfDumpFileManager().findMostRecentDump(DumpContentType.JSON).getDateStamp();

        outputFile = new BufferedWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename.replace("{DUMPDATE}", timestamp)), StandardCharsets.UTF_8)));

        EntityFactsHelpers.get().load();
        App.processEntitiesFromWikidataDump(dumpProcessingController, this);
        EntityFactsHelpers.get().save();

        outputFile.close();
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {

        String prop01 = null;
        String prop02 = null;

        for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
            final String propId = statementGroup.getProperty().getId();
            if (propId.equalsIgnoreCase(PROP01)) {
                prop01 = getStringValue(statementGroup);
            } else if (propId.equalsIgnoreCase(PROP02)) {
                prop02 = getStringValue(statementGroup);
            }
        }

        if (prop01 != null && prop02 != null) {
            if (prop01.equalsIgnoreCase(prop02)) {
                try {
                    outputFile.write(itemDocument.getItemId() + "\t" + prop01);
                    outputFile.newLine();
                } catch (IOException ex) {
                    LOG.error("Could not write to file", ex);
                }

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

}
