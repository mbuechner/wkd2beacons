# wkd2beacons - A Generator for BEACON files: [Gemeinsame Normdatei (GND)](https://en.wikipedia.org/wiki/Integrated_Authority_File) ↔ Wikidata 
Maven documentation: https://mbuechner.github.io/wkd2beacons/

## Compile
```sh
> mvn clean package
```

## Run
Start immediately with [default](https://github.com/mbuechner/wkd2beacons/blob/master/src/main/resources/config.xml) configuration:
```sh
> java -jar wkd2beacons.jar
```

Print help text:
```sh
> java -jar wkd2beacons.jar -h
```
```
usage: java -jar wkd2beacons.jar [-d <arg>] [-h] [-o <arg>] [-v]
 -d <arg>   Folder to stored all downloaded Wikidata dumps and entity type
            database (default: data)
 -h         Print help text
 -o <arg>   Destination folder (default: beacons)
 -v         Print version
```

## Requirements
- **Wikidata dump**: wkd2beacons will automatically download the newest [Wikidata dumps](https://dumps.wikimedia.org/other/wikidata/)
- **Entity Facts:** Internet connection and access to the [Entity Facts](http://www.dnb.de/DE/Service/DigitaleDienste/EntityFacts/entityfacts_node.html) data service
- **Entity type database:** see below

### Entity type database
wkd2beacons needs to know the entity type (e.g. person, place, family, organization) of a GND entity to decide which depiction is useful for the BEACON file. The tool will create a new local database (*File name schama:* ``data/entities-{YYYYMMDD}.db``) with its first run (That will take much longer time!). For the next runtime wkd2beacons will reuse this database and *not* ask Entity Facts service again (That's much faster!).

|                                                 | Runtime          |
|-------------------------------------------------|------------------|
| First run (building local entity type database) | 3day 7hour 4min  |
| Next run(s) (using local entity type database ) | 0day 2hour 17min |

**It's recommended to rebuild the local Entity type database regularly. Data will become obsolete and have a negative affects.**

## Output
*File name schema:* ``<dateOfDump>-beacon-<kindOfData>.txt``

### Examples
| File name                            | Example                                                                                                 | Desciption                                                                  |
|--------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| ``20180813-beacon_dewiki.txt``       | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_dewiki.txt)       | Concordance GND to German Wikipedia                                         |
| ``20180813-beacon_dewikisource.txt`` | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_dewikisource.txt) | Concordance GND to German Wikisource                                        |
| ``20180813-beacon_enwiki.txt``       | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_enwiki.txt)       | Concordance GND  to English Wikipedia                                       |
| ``20180813-beacon_enwikisource.txt`` | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_enwikisource.txt) | Concordance GND  to English Wikisource                                      |
| ``20180813-beacon_gndimages.txt``    | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_gndimages.txt)    | Concordance GND  to Images of (GND-) Persons, Families and Corporate Bodies |
| ``20180813-beacon_wikidata.txt``     | [Link](https://github.com/mbuechner/wkd2beacons/blob/master/downloads/20180813-beacon_wikidata.txt)     | Concordance GND  to Wikidata                                                |
| ``20180813-beacon_gndimages.csv``    | -                                                                                                       | CSV-Data for analysis of Wikidata images                                    |
