/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package repositories

import com.google.inject.{ImplementedBy, Inject, Singleton}
import models.{CalculationRequest, GmpCalculationResponse}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.Logging
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}


case class CachedCalculation(request: Int,
                             response: GmpCalculationResponse,
                             createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))

object CachedCalculation {

  implicit val formats: OFormat[CachedCalculation] = Json.format[CachedCalculation]
}

@ImplementedBy(classOf[CalculationMongoRepository])
trait CalculationRepository {

  def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]]

  def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean]

}

@Singleton
class CalculationMongoRepository @Inject()(mongo: MongoComponent, implicit val executionContext: ExecutionContext)
  extends PlayMongoRepository[CachedCalculation](
    collectionName = "calculation",
    mongoComponent = mongo,
    domainFormat = CachedCalculation.formats,
    indexes = Seq.empty
  ) with CalculationRepository with Logging {

  val fieldName = "createdAt"
  val createdIndexName = "calculationResponseExpiry"
  val expireAfterSeconds = "expireAfterSeconds"
  val timeToLive = 600

  createIndex(fieldName, createdIndexName, timeToLive)


  private def createIndex(field: String, indexName: String, ttl: Int): Future[Boolean] = {

    val mongoIndexes: Seq[IndexModel] = Seq(
      IndexModel(
        Indexes.ascending(field),
        IndexOptions()
          .name(indexName)
          .expireAfter(ttl, TimeUnit.SECONDS)
      )
    )
    collection.createIndexes(mongoIndexes).toFuture().map{
      _ =>
      logger.debug(s"set [$indexName] with value $ttl")
      true
    }.recover {
      case e => logger.error("Failed to set TTL index", e)
        false
    }
  }

  override def findByRequest(request: CalculationRequest): Future[Option[GmpCalculationResponse]] = {
    collection
      .find(Filters.equal("request", request.hashCode))
      .collect()
      .toFuture()
      .map { calculations =>
        logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, result: $calculations }")
        calculations.headOption.map(_.response)
      }
      .recover {
        case e =>
          logger.debug(s"[CalculationMongoRepository][findByRequest] : { request : $request, exception: ${e.getMessage} }")
          None
      }
  }

  override def insertByRequest(request: CalculationRequest, response: GmpCalculationResponse): Future[Boolean] = {
    val dataToInsert = CachedCalculation(request.hashCode, response)
    collection
      .insertOne(dataToInsert)
      .toFuture()
      .map { insertedData =>
        logger.debug(s"[CalculationMongoRepository][insertByRequest] : { request : $request, response: $response, result: ${insertedData.getInsertedId} }")
        true
      }.recover {
      case e => logger.error("Failed to insert  By request", e)
        false
    }
  }
}
