/*
 * Copyright 2016 HM Revenue & Customs
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

import sbt._

object MicroServiceBuild extends Build with MicroService {

  import scala.util.Properties.envOrElse

  val appName = "gmp"
  val appVersion = envOrElse("GMP_VERSION", "999-SNAPSHOT")

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val playHealthVersion = "2.0.0"
  private val playMicroserviceBootstrap = "5.8.0"
  private val playConfig = "3.0.0"
  private val playUrlBinders = "2.0.0"
  private val playAuthorisationVersion = "4.2.0"
  //private val playJsonLogger = "2.1.1"
  private val logbackJsonLogger = "3.1.0"
  private val playReactivemongoVersion = "5.1.0"
  //private val playMetrics = "2.3.0_0.2.1"
  private val playGraphite = "3.1.0"
  private val metricsGraphite = "3.0.2"

  val compile = Seq(
    "uk.gov.hmrc" %% "microservice-bootstrap" % playMicroserviceBootstrap,
    "uk.gov.hmrc" %% "play-url-binders" % playUrlBinders,
    "uk.gov.hmrc" %% "play-config" % playConfig, ws,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    //"com.kenshoo" %% "metrics-play" % playMetrics,
    "uk.gov.hmrc" %% "play-graphite" % playGraphite,
    //"com.codahale.metrics" % "metrics-graphite" % metricsGraphite,// Check it
    "uk.gov.hmrc" %% "play-authorisation" % playAuthorisationVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    //"uk.gov.hmrc" %% "play-json-logger" % playJsonLogger
    "uk.gov.hmrc" %% "logback-json-logger" % logbackJsonLogger
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = Seq.empty
  }

  private val scalatestVersion = "2.2.6"
  private val scalatestPlusPlayVersion = "1.5.1"
  private val pegdownVersion = "1.6.0"
  private val reactiveMongoTest = "1.6.0"

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "microservice-bootstrap" % playMicroserviceBootstrap % scope,
        "uk.gov.hmrc" %% "play-url-binders" % playUrlBinders % scope,
        "uk.gov.hmrc" %% "play-config" % playConfig % scope,
        "org.scalatest" %% "scalatest" % scalatestVersion % scope, // Check it
        "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope, // Check it
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope, // Check it
        "uk.gov.hmrc" %% "hmrctest" % hmrctestVersion % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % reactiveMongoTest % scope
      )
    }.test
  }

  private val hmrctestVersion = "2.2.0"

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "microservice-bootstrap" % playMicroserviceBootstrap % scope,
        "uk.gov.hmrc" %% "play-url-binders" % playUrlBinders % scope,
        "uk.gov.hmrc" %% "play-config" % playConfig % scope,
        "org.scalatest" %% "scalatest" % scalatestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "hmrctest" % hmrctestVersion % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
