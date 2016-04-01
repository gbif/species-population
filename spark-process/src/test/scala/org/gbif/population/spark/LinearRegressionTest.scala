package org.gbif.population.spark

import org.junit.Assert._
import org.junit._
import org.scalatest.FunSpec
import org.gbif.population.spark._

import scala.collection.mutable.Stack

@Test
class LinearRegressionTest extends FunSpec {

    describe("A simple linear regression") {

        it("should cross y axis at 0 with a slope of 1") {
            var data = List((0.0,0.0), (1.0,1.0), (2.0,2.0))
            var result = LinearRegression.process(data);
            assert(result.c === 0.0)
            assert(result.m === 1.0)
        }
    }
}


