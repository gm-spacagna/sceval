package sam.sceval

object BinUtils {
  case class BinStats(startBinNumber: Int = 0, offset: Int = 0)

  def binnerFac[Model](partitionLastIndexes: Array[Map[Model, Long]],
                       numRecodsPerBin: Long): (Model, Long, Int) => Int = {
    val modelToBinStats: Map[Model, Array[BinStats]] =
      partitionLastIndexes.flatMap(_.keySet).toSet.foldLeft(Map.empty[Model, List[BinStats]])((modelToStats, model) =>
        partitionLastIndexes.foldLeft(modelToStats)((modelToStats, partition) =>
          modelToStats + (model -> ((partition.get(model), modelToStats.getOrElse(model, List(BinStats()))) match {
            case (Some(lastIndex), cum@(BinStats(startBinNumber, offset) :: _)) =>
              val newOffset = (lastIndex + 1 + offset) % numRecodsPerBin
              BinStats((startBinNumber + (lastIndex + offset) / numRecodsPerBin).toInt + (if (newOffset == 0) 1 else 0),
                newOffset.toInt) +: cum
            case (None, cum@(binStats :: _)) => binStats +: cum
          })))
      )
      .mapValues(_.reverse.toArray).map(identity)

    (model: Model, index: Long, partitionIndex: Int) => {
      val BinStats(startBinNumber, offset) = modelToBinStats(model)(partitionIndex)
      (startBinNumber + (index + offset) / numRecodsPerBin).toInt
    }
  }

  def resultingBinNumber(recordsPerBin: Int, totalRecords: Long): Long =
    if (totalRecords % recordsPerBin == 0) totalRecords / recordsPerBin else (totalRecords / recordsPerBin) + 1

  def optimizeRecordsPerBin(totalRecords: Long, desiredBinNum: Int): Long =
    (1 to (if (desiredBinNum < totalRecords) 1 + (totalRecords / desiredBinNum) else desiredBinNum).toInt)
    .minBy(recordsPerBin => math.abs(resultingBinNumber(recordsPerBin, totalRecords) - desiredBinNum))
}
