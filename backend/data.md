# Data preparation instructions

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

CREATE TABLE lep_records STORED AS orc AS
SELECT
  speciesKey,
  year,
  concat('POINT(', round(decimalLongitude,2), ' ', round(decimalLatitude,2), ')') as geom,
  count(*) AS speciesCount
FROM occurrence
WHERE
  orderKey=797 AND speciesKey IS NOT NULL AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY
  speciesKey, year, round(decimalLatitude,2), round(decimalLongitude,2);

CREATE TABLE lep_records_aggregate STORED AS orc AS
SELECT geom, year, sum(speciesCount) AS count
FROM lep_records
GROUP BY geom,year;

CREATE TABLE lep_species ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT speciesKey, geom, to_json(collect(CAST(year AS String), speciesCount)) AS yearCounts
FROM lep_records
GROUP BY speciesKey, geom;

CREATE TABLE lep_group ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT geom, to_json(collect(CAST(year AS String), count)) AS yearCounts
FROM lep_records_aggregate
GROUP BY geom;

CREATE TABLE lep_names ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT speciesKey, scientificName
FROM prod_b.occurrence_hdfs
WHERE
  orderKey=797 AND speciesKey IS NOT NULL AND speciesKey = taxonKey AND
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
hdfs dfs -getmerge /user/hive/warehouse/tim.db/lep_species /tmp/lep_species.csv
hdfs dfs -getmerge /user/hive/warehouse/tim.db/lep_group /tmp/lep_group.csv
hdfs dfs -getmerge /user/hive/warehouse/tim.db/lep_names /tmp/lep_names.csv

-- to local
scp root@prodgateway-vh.gbif.org:/tmp/lep_species.csv /tmp/lep_species.csv
scp root@prodgateway-vh.gbif.org:/tmp/lep_group.csv /tmp/lep_group.csv
scp root@prodgateway-vh.gbif.org:/tmp/lep_names.csv /tmp/lep_names.csv

-- to db server
scp /tmp/lep_species.csv root@my1.gbif.org:/tmp/lep_species.csv
scp /tmp/lep_group.csv root@my1.gbif.org:/tmp/lep_group.csv
scp /tmp/lep_names.csv root@my1.gbif.org:/tmp/lep_names.csv
```

The final step involves loading data into MySQL: 

```
CREATE TABLE lepidoptera_import  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom TEXT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

LOAD DATA LOCAL INFILE '/tmp/lep_species.csv' INTO TABLE lepidoptera_import;

CREATE TABLE lepidoptera  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom POINT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

INSERT INTO lepidoptera SELECT speciesKey,PointFromText(geom),yearCounts FROM lepidoptera_import;

ALTER TABLE lepidoptera ADD INDEX lepidoptera_lookup1(speciesKey), ADD INDEX lepidoptera_lookup(speciesKey, geom);

DROP TABLE lepidoptera_import;

CREATE TABLE lepidoptera_group_import  (
  geom TEXT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

LOAD DATA LOCAL INFILE '/tmp/lep_group.csv' INTO TABLE lepidoptera_group_import;

CREATE TABLE lepidoptera_group  (
  geom POINT NOT NULL,
  yearCounts TEXT NOT NULL
) ENGINE = MyISAM;

INSERT INTO lepidoptera_group SELECT PointFromText(geom),yearCounts FROM lepidoptera_group_import;

ALTER TABLE lepidoptera_group ADD INDEX lepidoptera_group_lookup1(geom);

DROP TABLE lepidoptera_group_import;

CREATE TABLE lepidoptera_names (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  scientificName VARCHAR(255) NOT NULL,
  PRIMARY KEY(speciesKey),
  INDEX lookup(scientificName)
) ENGINE = MyISAM;
LOAD DATA LOCAL INFILE '/tmp/lep_names.csv' INTO TABLE lepidoptera_names;

```
