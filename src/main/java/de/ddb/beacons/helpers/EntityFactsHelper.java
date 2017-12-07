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
package de.ddb.beacons.helpers;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.LoggerFactory;

public class EntityFactsHelper {

    private static EntityFactsHelper efh;
    private Map<String, EntityType> entities;
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(EntityTimerProcessor.class);

    public enum EntityType {
        PERSON("Person"), ORGANISATION("Organisation"), OTHER("Other"), NONE("None"), ERROR("Error");

        private final String entityType;

        private EntityType(String value) {
            entityType = value;
        }

        public String getEntityTypeDescription() {
            return entityType;
        }
    }

    private EntityFactsHelper() {
    }

    public void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("entities.db"))) {
            oos.writeObject(entities);
        } catch (IOException ex) {
            LOG.error(ex.getMessage());
        }
    }

    public void load() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("entities.db"))) {
            entities = new HashMap<>();
            entities = (HashMap) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            LOG.warn(ex.getMessage());
            entities = new HashMap<>();
        }
    }

    public synchronized static EntityFactsHelper get() {
        if (efh == null) {
            EntityFactsHelper.efh = new EntityFactsHelper();
        }
        return EntityFactsHelper.efh;
    }

    public EntityType getEntityType(String gndId) {

        if (entities.containsKey(gndId)) {
            return entities.get(gndId);
        }

        try {
            URL url = new URL("http://hub.culturegraph.org/entityfacts/" + gndId);
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.connect();

            if (conn.getResponseCode() == 404) {
                entities.put(gndId, EntityType.NONE);
                return EntityType.NONE;
            }
            // test if request was successful (status 200)
            if (conn.getResponseCode() != 200) {
                entities.put(gndId, EntityType.ERROR);
                return EntityType.ERROR;
            }

            Reader reader = new InputStreamReader(conn.getInputStream());

            final JSONParser parser = new JSONParser();
            final KeyFinder finder = new KeyFinder();
            finder.setMatchKey("@type");
            while (!finder.isEnd()) {
                parser.parse(reader, finder, true);
                if (finder.isFound()) {
                    final String eType = finder.getValue().toString();
                    if (eType.equalsIgnoreCase("person")) {
                        entities.put(gndId, EntityType.PERSON);
                        return EntityType.PERSON;
                    } else if (eType.equalsIgnoreCase("organisation")) {
                        entities.put(gndId, EntityType.ORGANISATION);
                        return EntityType.ORGANISATION;
                    } else {
                        entities.put(gndId, EntityType.OTHER);
                        return EntityType.OTHER;
                    }
                }

            }
        } catch (ParseException | IOException ex) {
            return EntityType.ERROR;
        }

        return EntityType.NONE;
    }
}
