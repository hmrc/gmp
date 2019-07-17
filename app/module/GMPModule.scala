/*
 * Copyright 2019 HM Revenue & Customs
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

package module

import com.google.inject.{Provides, Singleton}
import config.{MicroserviceAuditConnector, WSHttp}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import repositories.{CalculationRepository, CalculationRepositoryProvider, ValidateSconRepository, ValidateSconRepositoryProvider}
import uk.gov.hmrc.http.{HttpDelete, HttpGet, HttpPost, HttpPut}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class GMPModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ValidateSconRepository].toProvider(classOf[ValidateSconRepositoryProvider]),
    bind[CalculationRepository].toProvider(classOf[CalculationRepositoryProvider]),
    bind[AuditConnector].to(MicroserviceAuditConnector),
    bind[HttpGet].to(WSHttp),
    bind[HttpPut].to(WSHttp),
    bind[HttpPost].to(WSHttp),
    bind[HttpDelete].to(WSHttp),
    bind[WSHttp].to(WSHttp)
  )

@Provides
@Singleton
  def mongoDB(reactiveMongoComponent: ReactiveMongoComponent): () => DefaultDB = reactiveMongoComponent.mongoConnector.db
}
