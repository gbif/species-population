
Start by using Hive to group data into grids.
```SQL
CREATE TABLE tim.lepidoptera_points STORED AS parquet AS
SELECT
  speciesKey,
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
  year IS NOT NULL AND year >= 1900 AND
  basisOfRecord != "FOSSIL_SPECIMEN" AND basisOfRecord != "LIVING_SPECIMEN"
GROUP BY speciesKey, year, decimalLatitude, decimalLongitude;
```

Get a spark shell open, using the spark 1.5.2 setup Oliver left configured
```
ssh root@prodgateway-vh
su - oliver
source /home/oliver/git/spark-duplicate-detection/src/main/resources/canned_hive_classpath.sh
~/spark-1.5.2/bin/spark-shell --master yarn --jars $HIVE_CLASSPATH --num-executors 20 --executor-memory 4g --executor-cores 1
```

For development only.
To turn a multipart parquet file into a single file to get from HDFS:
```
val df = sqlContext.read.parquet("/user/hive/warehouse/tim.db/lepidoptera_points")
df.repartition(1).saveAsParquetFile("/user/hive/warehouse/tim.db/lepidoptera_points_single.parquet")

hdfs dfs -get /user/hive/warehouse/tim.db/lepidoptera_points_single.parquet/part-r-00000-a51d3d63-8663-4a84-9d18-fd7d59445d11.gz.parquet /tmp/lepidoptera_points.parquet


```





Now process the linnear regression
```
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.LinearRegressionModel
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.linalg.Vectors

val df = sqlContext.read.parquet("/user/hive/warehouse/tim.db/lepidoptera_grid_10")
df.repartition(1).saveAsParquetFile("/user/hive/warehouse/tim.db/lepidoptera_grid_10.parquet")
df.show()

val df = sqlContext.read.parquet("/Users/tim/dev/data/lepidoptera_grid_10.parquet")
df.show()


df.repartition(100)
df.cache()

// turn into an RDD of objects to make easier to read 
case class GridCount(speciesKey: Int, year: Int, x: Long, y: Long, count: Long)
val data = df.map(row => {
  new GridCount(
    row.getInt(row.fieldIndex("specieskey")),
    row.getInt(row.fieldIndex("year")),
    row.getLong(row.fieldIndex("x")),
    row.getLong(row.fieldIndex("y")),
    row.getLong(row.fieldIndex("count"))
  )
})
data.cache()


// utility for the regression
class X_X2_Y(val x: Double, val x2: Double, val y: Double) {
  def this(p: (Double,Double)) = this(p._1, p._1*p._1, p._2)
  def +(p: X_X2_Y): X_X2_Y = new X_X2_Y(x+p.x,x2+p.x2,y+p.y)
}

// utility for the regression
class X2_Y2_XY(val x2: Double, val y2: Double, val xy: Double) {
  def this(p: (Double,Double), bars: (Double,Double)) = this((p._1-bars._1)*(p._1-bars._1), (p._2-bars._2)*(p._2-bars._2),(p._1-bars._1)*(p._2-bars._2))
  def +(p: X2_Y2_XY): X2_Y2_XY = new X2_Y2_XY(x2+p.x2,y2+p.y2,xy+p.xy)
}

// Takes a collection of KVPs, builds a simple linear regression model of them, and returns the slope
def slope(data : Iterable[(Int, Long)]) : Double = {
  val pairs = data.map(pair => (pair._1.toDouble, pair._2.toDouble)).toList
  val size = pairs.size

  // first pass: read in data, compute xbar and ybar
  val sums = pairs.foldLeft(new X_X2_Y(0D,0D,0D))(_ + new X_X2_Y(_))
  val bars = (sums.x / size, sums.y / size)

  // second pass: compute summary statistics
  val sumstats = pairs.foldLeft(new X2_Y2_XY(0D,0D,0D))(_ + new X2_Y2_XY(_, bars))

  val beta1 = sumstats.xy / sumstats.x2
  val beta0 = bars._2 - (beta1 * bars._1)
  val betas = (beta0, beta1)

  //println("y = " + ("%4.3f" format beta1) + " * x + " + ("%4.3f" format beta0))
  
  return beta1;
}

// convert KVP to allow for grouping by species and cell
val kvp = data.map( d => ((d.speciesKey, d.x, d.y), (d.year, d.count)))
kvp.cache()


val result = kvp.groupByKey().map( t => (t._1, slope(t._2))).collect()

-- given a year (the feature), we want to predict count (the label)
val data = df.map(row => {
  new LabeledPoint(
    row.getLong(row.fieldIndex("count")).doubleValue,
    Vectors dense(row.getInt(row.fieldIndex("year")))
  )
})
data.cache()
-- made up values
val numIterations = 100
val stepSize = 0.00001
val lrModel = LinearRegressionWithSGD.train(data, numIterations, stepSize)

val dataPoint = data.first
val prediction = lrModel.predict(dataPoint.features)
println(prediction)


val points = df.map(row => {
  new LabeledPoint(row.getLong(row.fieldIndex("count")).doubleValue, 
  Vectors dense(
    row.getInt(row.fieldIndex("year")), 
})


val Array(trainingData, testData) = points.randomSplit(Array(0.7, 0.3))
trainingData.cache()

val numIterations = 100
-- val stepSize = 0.00001
-- val model = LinearRegressionWithSGD.train(trainingData, numIterations, stepSize)
val model = LinearRegressionWithSGD.train(trainingData, numIterations)

val dataPoint = trainingData.first

val valuesAndPreds = trainingData.map { point =>
  val prediction = model.predict(point.features)
  (point.label, prediction)
}
val MSE = valuesAndPreds.map{case(v, p) => math.pow((v - p), 2)}.mean()
println("training Mean Squared Error = " + MSE)

valuesAndPreds.foreach((result) => println(s"predicted label: ${result._1}, actual label: ${result._2}"))


```

