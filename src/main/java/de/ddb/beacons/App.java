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
package de.ddb.beacons;

import de.ddb.beacons.runners.BeaconGndImage;
import de.ddb.beacons.runners.BeaconGndWikidata;
import de.ddb.beacons.runners.BeaconGndWikipedia;
import java.io.IOException;

public class App {

    public static void main(String[] args) throws IOException {
        new BeaconGndImage().run();
        new BeaconGndWikidata().run();
        new BeaconGndWikipedia().run();
    }
}
