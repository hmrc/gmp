/*
 * Copyright 2025 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import java.util.Base64

@Singleton
class AppConfig @Inject()(implicit
                          configuration: Configuration,
                          servicesConfig: ServicesConfig,
                          val featureSwitches: FeatureSwitches
                         ) {

  import servicesConfig._

  def hipUrl: String = getString("microservice.services.hip.url")
  private val clientId: String = getString("microservice.services.hip.clientId")
  private val secret: String   = getString("microservice.services.hip.secret")

  def hipAuthorisationToken: String =
    Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))

  def hipEnvironmentHeader: (String, String) =
    "Environment" -> getString("microservice.services.hip.environment")

  // These are now constants
  def originatorIdKey: String           = Constants.OriginatorIdKey
  def originatorIdValue: String         = Constants.OriginatorIdValue
  def originatingSystem: String         = Constants.XOriginatingSystemHeader
  def transmittingSystem: String        = Constants.XTransmittingSystemHeader

  def isHipEnabled: Boolean = featureSwitches.hipIntegration.enabled
  def isIfsEnabled: Boolean = featureSwitches.ifsMigration.enabled
}
