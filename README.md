# wkd2beacons - A Generator for BEACON files: [Gemeinsame Normdatei (GND)](https://en.wikipedia.org/wiki/Integrated_Authority_File) ↔ Wikidata 
Maven documentation: https://mbuechner.github.io/wkd2beacons/
Version: 1.2 (21-01-2019)
## Compile
### Fat-Jar Package with all Dependencies
```sh
> mvn clean package
```
### Dokumentation
```sh
> mvn clean site
```
## Download
https://github.com/mbuechner/wkd2beacons/raw/master/downloads/wkd2beacons.jar.gz
## Run
Start immediately with [default](https://github.com/mbuechner/wkd2beacons/blob/master/src/main/resources/config.xml) configuration:
```sh
> java -Dlog.file=wkd2beacons.log -jar wkd2beacons.jar
```
The parameter ``-Dlog.file=...`` defines the filename for the logging. Possible is just a file name ``-Dlog.file=wkd2beacons.log`` or (better) a path with filename ``-Dlog.file=/home/user/logs/20190121-wkd2beacons.log``. If there's no file defined, logging output will be in a file called ``log.file_IS_UNDEFINED``.

Print help text:
```sh
> java -Dlog.file=wkd2beacons.log -jar wkd2beacons.jar -h
```
```
usage: java -Dlog.file=wkd2beacons.log -jar wkd2beacons.jar [-d <arg>] [-h] [-o <arg>] [-v]
 -d <arg>   Folder to stored all downloaded Wikidata dumps and entity type database (default: data/)
 -h         Print help text
 -o <arg>   Destination folder (default: beacons/)
 -v         Print version
```

## Requirements
- **Wikidata dump**: wkd2beacons will automatically download the newest [Wikidata dumps](https://dumps.wikimedia.org/other/wikidata/)
- **Entity Facts:** Internet connection and access to the [Entity Facts](http://www.dnb.de/DE/Service/DigitaleDienste/EntityFacts/entityfacts_node.html) data service
- **Entity type database:** see below

### Entity type database
wkd2beacons needs to know the entity type (e.g. person, place, family, organization, event) of a GND entity to decide which depiction is useful for the BEACON file. 

| GND entity type | Preferred Wikidata property | Second-possible Wikidata property |
|-----------------|-----------------------------|-----------------------------------|
| Organization    |  Logo (P154)                | Image (P18)                       |
| Person          | Image (P18)                 | --                                |
| Family          | Crest (P94)                 | Image (P18)                       |
| Event           | Logo (P154)                 | Image (P18)                       |
| Place           | Image (P18)                 | --                                |
| n/a             | Logo (P154)                 | Image (P18)                       |

The tool will create a local database (*File name schama:* ``data/entities-{YYYYMMDD}.db``) with its first run (That will take much longer time!). For the next runtime wkd2beacons will reuse this database and *not* ask Entity Facts service again (That's much faster!).

|                                                 | Download of Wikidata dumps     | Runtime                         |
|-------------------------------------------------|--------------------------------|---------------------------------|
| First run(s) (using local entity type database) | 0 days 5 hours 23 min. 51 sec. | 2 days 13 hours 12 min. 12 sec. |
| Next run(s) (using local entity type database)  | approx. same                   | 0 days 2 hours 17 min. 20 sec.  |

**Caution:** It's recommended to rebuild the local Entity type database regularly. Data will become obsolete and have negative affects.

## Output
*File name schema:* ``<dateOfDump>-beacon-<kindOfData>.txt``

### Examples
| File name                            | Example                                                                                                 | Desciption                                                                  |
|--------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| ``20190114-beacon_dewiki.txt``       | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_dewiki.txt)       | Concordance GND to German Wikipedia                                         |
| ``20190114-beacon_dewikisource.txt`` | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_dewikisource.txt) | Concordance GND to German Wikisource                                        |
| ``20190114-beacon_enwiki.txt``       | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_enwiki.txt)       | Concordance GND  to English Wikipedia                                       |
| ``20190114-beacon_enwikisource.txt`` | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_enwikisource.txt) | Concordance GND  to English Wikisource                                      |
| ``20190114-beacon_gndimages.txt``    | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_gndimages.txt)    | Concordance GND  to Images of (GND-) Persons, Families and Corporate Bodies |
| ``20190114-beacon_wikidata.txt``     | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20190114-beacon_wikidata.txt)     | Concordance GND  to Wikidata                                                |
| ``20190114-beacon_gndimages.csv``    | -                                                                                                       | CSV-Data for analysis of Wikidata images                                    |
