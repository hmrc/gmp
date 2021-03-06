/*
 * Copyright 2021 HM Revenue & Customs
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

package config

import com.google.inject.{AbstractModule, Provides, Singleton}
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import repositories.{CalculationRepository, CalculationRepositoryProvider, ValidateSconRepository, ValidateSconRepositoryProvider}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.http.HttpClient

class GMPModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  def configure(): Unit = {
    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[AuditConnector]).to(classOf[DefaultAuditConnector])
    bind(classOf[CalculationRepository]).toProvider(classOf[CalculationRepositoryProvider])
    bind(classOf[ValidateSconRepository]).toProvider(classOf[ValidateSconRepositoryProvider])

  }

  @Provides
  @Singleton
  def mongoDB(reactiveMongoComponent: ReactiveMongoComponent): () => DefaultDB = reactiveMongoComponent.mongoConnector.db
}