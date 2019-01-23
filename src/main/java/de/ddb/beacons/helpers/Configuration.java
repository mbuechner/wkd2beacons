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

import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Michael Büchner
 */
public class Configuration {
    
    private static Configuration ch;
    private Properties prop;
    private final static String CONFIG_FILE_NAME = "config.xml";
    private final static Logger LOG = LoggerFactory.getLogger(Configuration.class);
    
    private Configuration() {        
        try {
            this.prop = new Properties();
            this.prop.loadFromXML(getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME));
        } catch (IOException ex) {
            LOG.error(ex.getLocalizedMessage());
        }
    }
    
    public synchronized static Configuration get() {
        try {
            if (ch == null) {
                Configuration.ch = new Configuration();
            }
            return Configuration.ch;
        } catch (Exception ex) {
            LOG.error(ex.getLocalizedMessage());
        }
        return null;
    }
    
    public String getValue(String key) {
        try {
            return prop.getProperty(key);
        } catch (Exception ex) {
            LOG.error(ex.getLocalizedMessage());
        }
        return null;
    }
    
    public void setValue(String key, String value) {
        LOG.info("{} is set to {}.", key, value);
        prop.setProperty(key, value);
    }
}
