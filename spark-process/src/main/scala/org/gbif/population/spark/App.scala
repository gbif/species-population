package org.gbif.population.spark

import org.apache.commons.math3.stat.regression.SimpleRegression
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.sql._
import org.apache.spark.sql.functions._

// container for the final result (can't be nested as the typetag must be visible for the Parquet schema writing)
case class Result(speciesKey: Int, x: Long, y: Long, x1: Long, y1: Long, slope: Double)

/**
  * Processes point based observation data into a tile pyramid per species.
  * Within each tile, data is gridded to cells and for each cell a simple linear regression of count per year is
  * performed.  The regression model is returned, along with the counts per year.
  */
object App {

  /**
    * For the given tuple of (year, count) determine the slope of the line
    */
  def slope(data : Iterable[(Int, Long)]) : Double = {
    val pairs = data.map(pair => Array(pair._1.toDouble, pair._2.toDouble)).toList
    val regression = new SimpleRegression();
    regression.addData(pairs.toArray)
    return regression.getSlope
  }


  def main(args : Array[String]) {
    val conf = new SparkConf()
      .setAppName("Species population trends")
      .setMaster("local[2]")
    val sqlContext = new org.apache.spark.sql.SQLContext(new SparkContext(conf))

    val df = sqlContext.read.parquet("/Users/tim/dev/data/lepidoptera_points.parquet")
    df.show()

    val z1 = 4;
    val zoomAhead = 5; // 256, 128, 64, 32, 16, 8, 4, 2, 1

    val griddedCounts = df.map(row => {

      val lat = row.getDouble(row.fieldIndex("lat"))
      val lng = row.getDouble(row.fieldIndex("lng"))

      // the tile coordinates at the zoom level
      val tileCoords = MercatorUtils.toXY(lat, lng, z1)

      // the cell coordinates, which are the tile coordinates when peeking ahead in the zoom
      val cellCoords = MercatorUtils.toXY(lat,  lng, z1 + zoomAhead)

      // return a KVP grouping by the cell
      ((row.getInt(row.fieldIndex("specieskey")),
        row.getInt(row.fieldIndex("year")),
        tileCoords.getX.toLong,
        tileCoords.getY.toLong,
        cellCoords.getX.toLong,
        cellCoords.getY.toLong),
        (row.getLong(row.fieldIndex("count"))))
    }).reduceByKey((a, b) => a + b)

    // regroup by the cell and perform the regression
    var result = griddedCounts.map( pair => {
      // regroup by the cell
      val(speciesKey, year, x, y, x1, y1) = pair._1
      val count = pair._2
      ((speciesKey, x, y, x1, y1),(year,count))
    }).groupByKey(100)
    .map( pair => {
      val(speciesKey, x, y, x1, y1) = pair._1
      val slopeResult = slope(pair._2) // year, count
      new Result(speciesKey, x, y, x1, y1, slopeResult)
    })

    // save the result
    sqlContext
      .createDataFrame(result.toJavaRDD())
      .repartition(1)
      .write
      .parquet("/tmp/result.parquet")
  }
}
