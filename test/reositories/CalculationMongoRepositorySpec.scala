package reositories

import models.{CalculationRequest, GmpCalculationResponse}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import repositories.{CachedCalculation, CalculationMongoRepository, DataMigrationRepository}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.Await

class CalculationMongoRepositorySpec extends AnyWordSpec
  with DefaultPlayMongoRepositorySupport[CachedCalculation]
  with Matchers
  with ScalaFutures {
  override lazy val repository: PlayMongoRepository[CachedCalculation] = new CalculationMongoRepository(mongoComponent)
  val repo = repository.asInstanceOf[CalculationMongoRepository]

  val calculationRequest =  CalculationRequest("S2730000B", "AA000004A", "BILLING", "MARCUS", None)

  val response = GmpCalculationResponse(
    name = "MARCUS BILLING",
    nino = "AA000004A",
    scon = "S2730000B",
    revaluationRate = None,
    revaluationDate = None,
    calculationPeriods = List.empty,
    globalErrorCode = 56069,
    spaDate = None,
    payableAgeDate = None,
    dateOfDeath = None,
    dualCalc = false,
    calcType = 0)

  "Find By request" in {

    val dataToInsert = CachedCalculation()
    Await.result(repo.insertByRequest())
  }
}
