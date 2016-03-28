import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark._

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
