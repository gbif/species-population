-- Hive script to prepare data for the Vector Tile server

USE tim;

-- create a source talle
/*
CREATE TABLE lep_source STORED AS ORCFILE AS
SELECT
  speciesKey,
  scientificName,
  year,
  decimalLatitude AS lat,
  decimalLongitude AS lng,
  count(*) AS count
FROM prod_b.occurrence_hdfs
WHERE
  orderKey=797 AND speciesKey IS NOT NULL AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1970 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY speciesKey, scientificName, year, decimalLatitude, decimalLongitude;

ADD JAR hdfs://prodmaster1-vh.gbif.org:8020/user/trobertson/hive-udfs-1.0-SNAPSHOT14.jar;
CREATE TEMPORARY function tile AS 'org.gbif.eubon.udf.TileUtilsUdf';

-- Notes to developers on Brickhouse UDFs:
-- 1. It's undocumented, but collect() does not support nested things like collect(concat_ws(...),year)
-- 2. It's undocumented, but to_json() only supports String type keys
ADD JAR hdfs://prodmaster1-vh.gbif.org:8020/user/trobertson/brickhouse-0.7.1-SNAPSHOT.jar;
CREATE TEMPORARY function collect AS 'brickhouse.udf.collect.CollectUDAF';
CREATE TEMPORARY function to_json AS 'brickhouse.udf.json.ToJsonUDF';

-- Run the UNION parts in parallel
SET hive.exec.parallel = true;
CREATE TABLE lep_2 STORED AS ORCFILE AS

SELECT speciesKey, 0 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 0) AS a, tile(lat, lng, 3) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 1 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 1) AS a, tile(lat, lng, 4) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 2 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 2) AS a, tile(lat, lng, 5) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 3 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 3) AS a, tile(lat, lng, 6) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 4 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 4) AS a, tile(lat, lng, 7) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 5 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 5) AS a, tile(lat, lng, 8) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 6 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 6) AS a, tile(lat, lng, 9) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year

UNION ALL

SELECT speciesKey, 7 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 7) AS a, tile(lat, lng, 10) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year


UNION ALL

SELECT speciesKey, 8 AS z, a.x AS x, a.y AS y, concat_ws(':', cast(b.x AS String),
  cast(b.y AS String)) AS cellID, cast(year AS String) AS year, count(*) AS count
FROM (SELECT speciesKey, year, tile(lat, lng, 8) AS a, tile(lat, lng, 11) AS b FROM lep_source) t
GROUP BY speciesKey, a.x, a.y, b.x, b.y, year;

-- TEMPORARY HACK: Add the group as a species
CREATE TABLE lep_2_b STORED AS ORCFILE AS
SELECT * FROM lep_2
UNION ALL
SELECT 797 AS speciesKey, z, x, y, cellID, year, sum(count) AS count
FROM lep_2
GROUP BY z, x, y, cellID, year;

DROP TABLE lep_3;
DROP TABLE lep_4;
DROP TABLE lep_5;
DROP TABLE lep_6;

CREATE TABLE lep_3 STORED AS ORCFILE AS
SELECT speciesKey, z, x, y, year, collect(cellID, count) AS cell
FROM lep_2_b
GROUP BY speciesKey, z, x, y, year;

CREATE TABLE lep_4 STORED AS ORCFILE AS
SELECT speciesKey, z, x, y, collect(year, cell) AS data
FROM lep_3
GROUP BY speciesKey, z, x, y;

CREATE TABLE lep_5
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT speciesKey, z, x, y, to_json(data) AS cells
FROM lep_4;
*/

-- For MySQL:
CREATE TABLE tiles(
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  z TINYINT UNSIGNED NOT NULL,
  x TINYINT UNSIGNED NOT NULL,
  y TINYINT UNSIGNED NOT NULL,
  json TEXT NOT NULL,
  PRIMARY KEY(speciesKey,z,x,y),
  INDEX lookup(speciesKey,z,x,y)
) ENGINE = MyISAM;
-- load data local infile '/tmp/lep2.csv' into table tiles;



CREATE TABLE tim.occurrence
STORED AS orc AS
SELECT
  gbifId,kingdomKey,phylumKey,classKey,orderKey,familyKey,genusKey,speciesKey,
  decimalLatitude, decimalLongitude, hasGeospatialIssues,
  year, month, day, basisOfRecord, datasetKey, publishingOrgKey, countryCode
FROM prod_b.occurrence_hdfs;


CREATE TABLE tim.lep_records_1
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT
  speciesKey,
    concat('POINT(', round(decimalLongitude,2), ' ', round(decimalLatitude,2), ')') as geom,
  year,
  count(*) AS speciesCount
FROM tim.occurrence
WHERE
  orderKey=797 AND speciesKey IS NOT NULL AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY
  speciesKey, year, round(decimalLatitude,2), round(decimalLongitude,2);


CREATE TABLE tim.lep_records_1_group
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT
  concat('POINT(', round(decimalLongitude,2), ' ', round(decimalLatitude,2), ')') as geom,
  year,
  count(*) AS groupCount
FROM tim.occurrence
WHERE
  orderKey=797 AND speciesKey IS NOT NULL AND
  decimalLatitude IS NOT NULL AND decimalLatitude BETWEEN -85 AND 85 AND
  decimalLongitude IS NOT NULL AND decimalLongitude BETWEEN -180 AND 180 AND
  hasGeospatialIssues = false AND
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != 'FOSSIL_SPECIMEN' AND basisOfRecord != 'LIVING_SPECIMEN'
GROUP BY
  year, round(decimalLatitude,2), round(decimalLongitude,2);



CREATE TABLE tim.lep_records_1_final
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
SELECT
  s.speciesKey,
  s.geom,
  s.year,
  s.speciesCount,
  g.groupCount as orderCount
FROM
  tim.lep_records_1 s
  JOIN tim.lep_records_1_group g ON s.geom=g.geom AND s.year=g.year;


-- For MySQL (with the wide table):
CREATE TABLE lepidoptera_import  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom TEXT NOT NULL,
  year SMALLINT UNSIGNED NOT NULL,
  speciesCount MEDIUMINT UNSIGNED NOT NULL,
  orderCount MEDIUMINT UNSIGNED NOT NULL
) ENGINE = MyISAM;

CREATE TABLE lepidoptera  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  geom POINT NOT NULL,
  year SMALLINT UNSIGNED NOT NULL,
  speciesCount MEDIUMINT UNSIGNED NOT NULL,
  orderCount MEDIUMINT UNSIGNED NOT NULL
) ENGINE = MyISAM;

INSERT INTO lepidoptera
SELECT speciesKey,PointFromText(geom),year,speciesCount,orderCount
FROM lepidoptera_import;

ALTER TABLE lepidoptera ADD INDEX lookup(speciesKey,geom);
ALTER TABLE lepidoptera ADD INDEX lookup2(speciesKey,year,geom);

CREATE TABLE lepidoptera_names  (
  speciesKey MEDIUMINT UNSIGNED NOT NULL,
  scientificName VARCHAR(255) NOT NULL,
  PRIMARY KEY(speciesKey),
  INDEX lookup(scientificName)
) ENGINE = MyISAM;
load data local infile '/tmp/lep_names.csv' into table lepidoptera_names;

Which is created in HIVE:
  CREATE TABLE tim.lep_names
  ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t' AS
  SELECT taxonKey, scientificName
  FROM prod_b.occurrence_hdfs
  WHERE orderKey=797 AND taxonKey=speciesKey
  GROUP BY scientificName, taxonKey
  HAVING COUNT()

SELECT
  X(geom),
  Y(geom),
  GROUP_CONCAT(CONCAT(year,',', speciesCount, ',',orderCount)) AS features
FROM lepidoptera
WHERE speciesKey=5124911
AND YEAR BETWEEN 1970 AND 2016
GROUP BY geom
HAVING count(year)>=2;


CREATE TABLE test AS
SELECT speciesKey, geom, GROUP_CONCAT(CONCAT(year,':', speciesCount)) AS yearCounts
FROM lepidoptera
GROUP BY speciesKey, geom;
ALTER TABLE test ADD INDEX lookup(speciesKey);
ALTER TABLE test ADD INDEX lookup(speciesKey,geom);


CREATE TABLE lepidoptera_group AS
SELECT geom, year, sum(speciesCount) as count
FROM lepidoptera
GROUP BY geom,year;
ALTER TABLE lepidoptera_group ADD INDEX lookup3(geom);

CREATE TABLE test2 AS
SELECT geom, GROUP_CONCAT(CONCAT(year,':', count)) AS yearCounts
FROM lepidoptera_group
GROUP BY geom;
ALTER TABLE test2 ADD INDEX lookup2(geom);
