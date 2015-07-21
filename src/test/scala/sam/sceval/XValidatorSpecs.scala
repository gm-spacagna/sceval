package sam.sceval

import org.apache.spark.rdd.RDD
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import Arbitrary.arbitrary

import scala.util.Try

class XValidatorSpecs extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential
  val sc = StaticSparkContext.staticSc

  val folds = 5
  val xvalidator = XValidator(folds = folds, evalBins = Some(4))
  implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

  // GMARIO: try to add a pattern e.g. 1 to 128 by power of 2 so that we don't have to hardcode the values.
  implicit val arbitraryListIntBool: Arbitrary[List[(Int, Boolean)]] =
    Arbitrary(Gen.oneOf(List(1, 2, 4, 8, 10, 15, 20, 50, 100).map(Gen.listOfN(_, arbitrary[(Int, Boolean)]))): _*)

  "XValidator" should {
    "split into fold folds and are same size (with possible off by ones)" ! check(prop(
      (featuresAndLabels: List[(Int, Boolean)], partitions: Int) => {
        val trySplit = Try(xvalidator.split(sc.makeRDD(featuresAndLabels, partitions)))
        // GMARIO: What if it fails? then our tests will be skipped.
        // Also using Try means we are wrapping any kind of exception while we should explicitily declare the ones we
        // expect to handle
        trySplit.isSuccess ==> {
          val foldSizeMap = trySplit.get.map(p => (p._1, 1)).reduceByKey(_ + _).collect().toMap
          foldSizeMap.size must_== folds

          val total = foldSizeMap.values.sum
          foldSizeMap.values.toSet must_===
          (if (total % folds == 0) Set(total / folds) else Set(total / folds, total / folds + 1))
        }
      }))

    "compute correct BinaryConfusionMatricies" in {
      val scoresAndLabelsByModel: RDD[(Int, Double, Boolean)] = sc.makeRDD(
        Map(
          0 -> List(
            (0.0, false),
            (0.2, false),
            (0.4, true),
            (0.6, false),
            (0.8, true)
          ),
          1 -> List(
            (0.1, true),
            (0.3, true),
            (0.7, false),

            (0.9, true),
            (1.0, true)
          )).flatMap {
          case (fold, examples) => examples.map {
            case (score, label) => (fold, score, label)
          }
        }
        .toSeq, 4)

      XValidator(folds = 2, evalBins = Some(2)).evaluate(scoresAndLabelsByModel) must_=== Array(
        BinaryConfusionMatrix(tp = 2, fp = 3, tn = 0, fn = 0) + BinaryConfusionMatrix(tp = 4, fp = 1, tn = 0, fn = 0),
        BinaryConfusionMatrix(tp = 1, fp = 1, tn = 2, fn = 1) + BinaryConfusionMatrix(tp = 2, fp = 0, tn = 1, fn = 2)
      )
    }
  }
}
