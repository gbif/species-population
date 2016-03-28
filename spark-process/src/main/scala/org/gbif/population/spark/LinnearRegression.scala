package org.gbif.population.spark

/**
  * Performs Simple Linear Regression.
  * Given a 2D matrix of points, will provide the best fit equation in the form y = mx+c along with the sum of squares
  * error (SSE), sum of squares regression (SSR) and the total sum of squares (SSTO).
  *
  * @see http://eric-mariacher.blogspot.dk/2011/12/here-is-how-to-compute-simple-linear.html
  */
object LinearRegression {

  /**
    * Container of the result of the regression.
    */
  class Result(val m: Double, val c: Double, val SSTO: Double, val SSR: Double, val SSE: Double);

  /**
    * Performs the regression.
    */
  def process(pairs: List[(Double,Double)]) : Result = {
    val size = pairs.size

    // first pass: read in data, compute xbar and ybar
    val sums = pairs.foldLeft(new X_X2_Y(0D,0D,0D))(_ + new X_X2_Y(_))
    val bars = (sums.x / size, sums.y / size)

    // second pass: compute summary statistics
    val sumstats = pairs.foldLeft(new X2_Y2_XY(0D,0D,0D))(_ + new X2_Y2_XY(_, bars))

    val beta1 = sumstats.xy / sumstats.x2
    val beta0 = bars._2 - (beta1 * bars._1)
    val betas = (beta0, beta1)

    // Useful debug (y= mx+c)
    // println("y = " + ("%4.3f" format beta1) + " * x + " + ("%4.3f" format beta0))

    val correlation = pairs.foldLeft(new RSS_SSR(0D,0D))(_ + RSS_SSR.build(_, bars, betas))
    //println("SSTO = " + sumstats.y2)      //
    //println("SSE  = " + correlation.rss)  // sum of squares error
    //println("SSR  = " + correlation.ssr)  // sum of squares regression

    return new Result(beta1, beta0, sumstats.y2, correlation.rss, correlation.ssr)
  }

  class RSS_SSR(val rss: Double, val ssr: Double) {
    def +(p: RSS_SSR): RSS_SSR = new RSS_SSR(rss+p.rss, ssr+p.ssr)
  }

  object RSS_SSR {
    def build(p: (Double,Double), bars: (Double,Double), betas: (Double,Double)): RSS_SSR = {
      val fit = (betas._2 * p._1) + betas._1
      val rss = (fit-p._2) * (fit-p._2)
      val ssr = (fit-bars._2) * (fit-bars._2)
      new RSS_SSR(rss, ssr)
    }
  }

  class X_X2_Y(val x: Double, val x2: Double, val y: Double) {
    def this(p: (Double,Double)) = this(p._1, p._1*p._1, p._2)
    def +(p: X_X2_Y): X_X2_Y = new X_X2_Y(x+p.x,x2+p.x2,y+p.y)
  }

  class X2_Y2_XY(val x2: Double, val y2: Double, val xy: Double) {
    def this(p: (Double,Double), bars: (Double,Double)) = this((p._1-bars._1)*(p._1-bars._1), (p._2-bars._2)*(p._2-bars._2),(p._1-bars._1)*(p._2-bars._2))
    def +(p: X2_Y2_XY): X2_Y2_XY = new X2_Y2_XY(x2+p.x2,y2+p.y2,xy+p.xy)
  }
}






