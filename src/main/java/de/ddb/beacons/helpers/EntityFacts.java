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
package de.ddb.beacons.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Büchner
 */
public class EntityFacts {

    private final static String DB_FILENAME_PREFIX = "entities-";
    private final static String DB_FILENAME_SUFFIX = "{{date}}.db";
    private final static String EF_URL = "http://hub.culturegraph.org/entityfacts/";
    private static EntityFacts efh;
    private Map<String, EntityType> entities;
    private final static ObjectMapper MAPPER = new ObjectMapper();
    private File loadedFile;

    private final static Logger LOG = LoggerFactory.getLogger(EntityFacts.class);

    public enum EntityType {

        PERSON("Person"), PLACE("Geografika"), FAMILY("Familie"), ORGANISATION("Organisation"), EVENT("Veranstaltung"), NA("Nicht verfügbar");

        private final String entityType;

        private EntityType(String value) {
            entityType = value;
        }

        public String getEntityTypeDescription() {
            return entityType;
        }
    }

    private EntityFacts() {
        entities = new HashMap<>();
        loadedFile = null;
    }

    public void save() {
        final String filename = DB_FILENAME_PREFIX + DB_FILENAME_SUFFIX.replace("{{date}}", new SimpleDateFormat("yyyyMMdd").format(new Date()));
        final File file = new File(Configuration.get().getValue("dataDir") + File.separator + filename);

        if (!file.exists()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(entities);
            } catch (IOException ex) {
                LOG.error(ex.getMessage());
            }
        } else {
            LOG.warn("Could not save local Entity Facts databased to {}, because file already exists.", file.getAbsoluteFile());
        }
        LOG.info("Local Entity Facts databased saved to {}.", file.getAbsoluteFile());

    }

    public void load() {

        final File dir = new File(Configuration.get().getValue("dataDir"));
        LOG.info("Loading entity type database from {}...", dir.getAbsolutePath());

        if (!dir.isDirectory()) {
            LOG.error("{} is not a directory.", Configuration.get().getValue("dataDir"));
            return;
        }
        final File[] files = dir.listFiles(new MyFileFilter());

        if (files.length > 1) {
            LOG.warn("Found the following entity type databases: {}", Arrays.toString(files));
            Arrays.sort(files);
            LOG.info("I have chosen wisely ... {}", files[files.length - 1].getName());
        } else if (files.length == 0) {
            LOG.warn("Could NOT found an entity type database. That will take long time, because all data need to be catched from Entity Facts service!");
            return;
        }

        this.loadedFile = files[files.length - 1];

        LOG.info("Loading entity type database from {}...", this.loadedFile.getName());

        try (final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(this.loadedFile))) {
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                final Map<String, EntityType> casted = (Map<String, EntityType>) obj;
                entities = casted;
                LOG.info("Entity type database has {} entries.", entities.size());
            } else {
                LOG.warn("Loaded object is not of expected type Map<String, EntityType>.");
                entities = new HashMap<>();
            }
        } catch (IOException | ClassNotFoundException ex) {
            LOG.warn("Error loading entity type database. {}", ex.getMessage());
        }
    }

    public synchronized static EntityFacts get() {
        if (efh == null) {
            EntityFacts.efh = new EntityFacts();
        }
        return EntityFacts.efh;
    }

    public EntityType getEntityType(String gndId) {

        LOG.debug("Getting entity type for {}...", gndId);
        if (entities != null && entities.containsKey(gndId)) {
            final EntityType et = entities.get(gndId);
            LOG.debug("Entity type of {} is '{}'.", gndId, et.getEntityTypeDescription());
            return et;
        }

        LOG.info("Entity type of {} is not in local database. Start asking Entity Facts...", gndId);
        final EntityType et = getEntityTypeFromEntityFacts(gndId);

        LOG.info("Entity type of {} is '{}'.", gndId, et.getEntityTypeDescription());
        return et;
    }

    private EntityType getEntityTypeFromEntityFacts(String gndId) {
        try {
            URL url = new URL(EF_URL + gndId);
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.connect();

            // test if request was successful (status 200)
            if (conn.getResponseCode() != 200) {
                entities.put(gndId, EntityType.NA);
                return EntityType.NA;
            }

            final Reader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
            final JsonNode root = MAPPER.readTree(reader);
            final String typeValue = root.path("@type").textValue();

            for (EntityType et : EntityType.values()) {
                if (typeValue.equalsIgnoreCase(et.toString())) {
                    entities.put(gndId, et);
                    return et;
                }
            }

            return EntityType.NA;

        } catch (IOException ex) {
            return EntityType.NA;
        }
    }

    private static class MyFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            if (!pathname.isFile()) {
                return false;
            }
            final String t = File.separator;
            final int i = pathname.toString().indexOf(t) + t.length();
            if (i == -1) {
                return false;
            }
            final String s = pathname.toString().substring(i);
            final Pattern r = Pattern.compile(DB_FILENAME_PREFIX + "[0-9]{8}" + DB_FILENAME_SUFFIX.replace("{{date}}", ""));
            final Matcher m = r.matcher(s);

            return m.find();
        }
    }

}
