/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.ddb.beacons.playground;

import de.ddb.beacons.helpers.EntityFactsHelper;
import de.ddb.beacons.helpers.ExampleHelpers;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;

/**
 *
 * @author buechner
 */
public class FindDuplicatesMain implements EntityDocumentProcessor {

    // first propertiy
    private final String PROP01 = "P18";
    // second property
    // private final String PROP02 = "P94";
    // private final String PROP02 = "P1801";
    private final String PROP02 = "P1442";

    private final String outputFilename = "{DUMPDATE}-" + PROP01 + "-to-" + PROP02 + ".txt";

    private final org.slf4j.Logger LOG = LoggerFactory.getLogger(FindDuplicatesMain.class);
    private Sites sites;
    private BufferedWriter outputFile;

    public static void main(String[] args) throws IOException {
        new FindDuplicatesMain().run();
    }

    private void run() throws IOException {

        ExampleHelpers.configureLogging();

        // get site urls
        DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        dumpProcessingController.setOfflineMode(ExampleHelpers.OFFLINE_MODE);

        final String timestamp = dumpProcessingController.getWmfDumpFileManager().findMostRecentDump(DumpContentType.JSON).getDateStamp();
        outputFile = new BufferedWriter(new FileWriter(outputFilename.replace("{DUMPDATE}", timestamp)));

        // Download the sites table dump and extract information
        sites = dumpProcessingController.getSitesInformation();

        EntityFactsHelper.get().load();
        ExampleHelpers.processEntitiesFromWikidataDump(this);
        EntityFactsHelper.get().save();

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
