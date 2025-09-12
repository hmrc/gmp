/*
 * Copyright 2024 HM Revenue & Customs
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
import models.GmpValidateSconResponse
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.Logging
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import utils.LoggingUtils

import java.time.{LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}


case class ValidateSconMongoModel(scon: String,
                                  response: GmpValidateSconResponse,
                                  createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC))

object ValidateSconMongoModel {
  implicit val formats: OFormat[ValidateSconMongoModel] = Json.format[ValidateSconMongoModel]
}

@ImplementedBy(classOf[ValidateSconMongoRepository])
trait ValidateSconRepository {
  def findByScon(scon: String): Future[Option[GmpValidateSconResponse]]

  def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean]
}

@Singleton
class ValidateSconMongoRepository @Inject()(mongo: MongoComponent, implicit val executionContext: ExecutionContext)
  extends PlayMongoRepository[ValidateSconMongoModel](
    collectionName = "validate_scon",
    mongoComponent = mongo,
    domainFormat = ValidateSconMongoModel.formats,
    indexes = Seq(IndexModel(
      Indexes.ascending("createdAt"),
      IndexOptions()
        .name("sconValidationResponseExpiry")
        .expireAfter(600, TimeUnit.SECONDS)
    ))
  ) with ValidateSconRepository with Logging {

  override def insertByScon(scon: String, validateSconResponse: GmpValidateSconResponse): Future[Boolean] = {
    val model = ValidateSconMongoModel(scon, validateSconResponse)
    collection
      .insertOne(model)
      .toFuture()
      .map { insertResult =>
      logger.debug(s"[ValidateSconMongoRepository][insertByScon] : { scon : $scon, result: ${insertResult.getInsertedId} }")
      insertResult.wasAcknowledged()
    }
  }

  override def findByScon(scon: String): Future[Option[GmpValidateSconResponse]] = {
    collection
      .find(Filters.equal("scon", scon))
      .collect()
      .toFuture()
      .map { response =>
        logger.debug(s"[ValidateSconMongoRepository][findByScon] : { scon : $scon, result: $response }")
        response.headOption.map(_.response)
      }.recover {
      case e =>
        logger.error(s"[ValidateSconMongoRepository][findByScon] Error finding SCON validation: ${LoggingUtils.redactError(e.getMessage)}")
        logger.debug(s"[ValidateSconMongoRepository][findByScon] Error details for SCON: $scon", e)
        None
    }

  }
}
