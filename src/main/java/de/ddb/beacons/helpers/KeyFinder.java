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

import java.io.IOException;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;

class KeyFinder implements ContentHandler {

    private Object value;
    private boolean found = false;
    private boolean end = false;
    private String key;
    private String matchKey;

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public Object getValue() {
        return value;
    }

    public boolean isEnd() {
        return end;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public boolean isFound() {
        return found;
    }

    @Override
    public void startJSON() throws ParseException, IOException {
        found = false;
        end = false;
    }

    @Override
    public void endJSON() throws ParseException, IOException {
        end = true;
    }

    @Override
    public boolean primitive(Object value) throws ParseException, IOException {
        if (key != null) {
            if (key.equals(matchKey)) {
                found = true;
                this.value = value;
                key = null;
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean startArray() throws ParseException, IOException {
        return true;
    }

    @Override
    public boolean startObject() throws ParseException, IOException {
        return true;
    }

    @Override
    public boolean startObjectEntry(String key) throws ParseException, IOException {
        this.key = key;
        return true;
    }

    @Override
    public boolean endArray() throws ParseException, IOException {
        return false;
    }

    @Override
    public boolean endObject() throws ParseException, IOException {
        return true;
    }

    @Override
    public boolean endObjectEntry() throws ParseException, IOException {
        return true;
    }
}
