# wkd2beacons - A Generator for GND/Wikidata BEACON files.

## Compile
**Compile:** ``mvn clean package``

**Run:** ``java -jar target/wkd2beacons.jar``

## Usage
**Source:** Wikidata dump - will download newest dump automatically

**Working file:** ``entities.db`` - database with GND IDs and entity type (person, corporate body etc.), will create new one with first run (that will take much longer time!) 

**Output:** ``<dateOfDump>-beacon-<kindOfData>.txt``

**Examples:**
- ``20161114-beacon_dewiki.txt`` -> Links to German Wikipedia
- ``20161114-beacon_dewikisource.txt`` ->  Links to German Wikisource
- ``20161114-beacon_enwiki.txt`` -> Links to English Wikipedia
- ``20161114-beacon_enwikisource.txt`` ->  Links to English Wikisource
- ``20161114-beacon_gndimages.txt`` -> Links to Images of (GND-) Persons, Families and Corporate Bodies
- ``20161114-beacon_wikidata.txt`` -> Links to Wikidata


- ``20161017-beacon_gndimages.csv`` -> CSV-Data for analysis of Wikidata images
