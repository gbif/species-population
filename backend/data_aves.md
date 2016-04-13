# Data preparation instructions

**This is a TEST!  For now it is a hacked copy of the Lepidoptera one.  Use with caution**

These instructions may require tweaking of paths and database names etc. but should be _trivial_ to follow.

The first step involves Hive:

```
USE tim;

-- Notes to developers on Brickhouse UDFs:
-- 1. It's undocumented, but collect() does not support nested things like collect(concat_ws(...),year)
-- 2. It's undocumented, but to_json() only supports String type keys
ADD JAR hdfs://prodmaster1-vh.gbif.org:8020/user/trobertson/brickhouse-0.7.1-SNAPSHOT.jar;
CREATE TEMPORARY function collect AS 'brickhouse.udf.collect.CollectUDAF';
CREATE TEMPORARY function to_json AS 'brickhouse.udf.json.ToJsonUDF';

-- take a small snapshot of occurrence to allow quick iterations of development
CREATE TABLE occurrence STORED AS orc AS
SELECT
  gbifId,kingdomKey,phylumKey,classKey,orderKey,familyKey,genusKey,speciesKey,
  decimalLatitude, decimalLongitude, hasGeospatialIssues,
  year, month, day, basisOfRecord, datasetKey, publishingOrgKey, countryCode,
  scientificName
FROM prod_b.occurrence_hdfs;

CREATE TABLE aves_records STORED AS orc AS
SELECT
  speciesKey,
  year,
  concat('POINT(', round(decimalLongitude,2), ' ', round(decimalLatitude,2), ')') as geom,
  count(*) AS speciesCount
FROM occurrence
WHERE
  classKey=212 AND speciesKey IS NOT NULL AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY
  speciesKey, year, round(decimalLatitude,2), round(decimalLongitude,2);

CREATE TABLE aves_records_aggregate STORED AS orc AS
SELECT geom, year, sum(speciesCount) AS count
FROM aves_records
GROUP BY geom,year;

CREATE TABLE aves_species ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT speciesKey, geom, to_json(collect(CAST(year AS String), speciesCount)) AS yearCounts
FROM aves_records
GROUP BY speciesKey, geom;

CREATE TABLE aves_group ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT geom, to_json(collect(CAST(year AS String), count)) AS yearCounts
FROM aves_records_aggregate
GROUP BY geom;

CREATE TABLE aves_names ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT speciesKey, scientificName
FROM prod_b.occurrence_hdfs
WHERE
  classKey=212 AND speciesKey IS NOT NULL AND speciesKey = taxonKey AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY scientificName, speciesKey
HAVING count(year) > 1;
```

The next step involves getting tab delimited files out of HDFS and onto the target MySQL machine:
```
-- export from hdfs
hdfs dfs -getmerge /user/hive/warehouse/tim.db/aves_species /tmp/aves_species.csv
hdfs dfs -getmerge /user/hive/warehouse/tim.db/aves_group /tmp/aves_group.csv
hdfs dfs -getmerge /user/hive/warehouse/tim.db/aves_names /tmp/aves_names.csv

-- to local
scp root@prodgateway-vh.gbif.org:/tmp/aves_species.csv /tmp/aves_species.csv
scp root@prodgateway-vh.gbif.org:/tmp/aves_group.csv /tmp/aves_group.csv
scp root@prodgateway-vh.gbif.org:/tmp/aves_names.csv /tmp/aves_names.csv

-- to db server
scp /tmp/aves_species.csv root@my1.gbif.org:/tmp/aves_species.csv
scp /tmp/aves_group.csv root@my1.gbif.org:/tmp/aves_group.csv
scp /tmp/aves_names.csv root@my1.gbif.org:/tmp/aves_names.csv
```

The final step involves loading data into MySQL: 

```
CREATE TABLE aves_import  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom TEXT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

LOAD DATA LOCAL INFILE '/tmp/aves_species.csv' INTO TABLE aves_import;

CREATE TABLE aves  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom POINT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

INSERT INTO aves SELECT speciesKey,PointFromText(geom),yearCounts FROM aves_import;

ALTER TABLE aves ADD INDEX aves_lookup1(speciesKey), ADD INDEX aves_lookup(speciesKey, geom);

DROP TABLE aves_import;

CREATE TABLE aves_group_import  (
  geom TEXT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

LOAD DATA LOCAL INFILE '/tmp/aves_group.csv' INTO TABLE aves_group_import;

CREATE TABLE aves_group  (
  geom POINT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

INSERT INTO aves_group SELECT PointFromText(geom),yearCounts FROM aves_group_import;

ALTER TABLE aves_group ADD INDEX aves_group_lookup1(geom);

DROP TABLE aves_group_import;

CREATE TABLE aves_names (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  scientificName VARCHAR(255) NOT NULL,
  PRIMARY KEY(speciesKey),
  INDEX lookup(scientificName)
) ENGINE = MyISAM;
LOAD DATA LOCAL INFILE '/tmp/aves_names.csv' INTO TABLE aves_names;

```
