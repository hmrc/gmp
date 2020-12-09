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

import scoverage._
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val akkaVersion     = "2.5.23"

val akkaHttpVersion = "10.0.15"


dependencyOverrides += "com.typesafe.akka" %% "akka-stream"    % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-protobuf"  % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-actor"     % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

val appName: String = "gmp"

lazy val plugins: Seq[Plugins] =
    Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

lazy val scoverageExcludePatterns = List(
  "<empty>",
  "app.*",
  "gmp.*",
  "config.*",
  "metrics.*",
  "testOnlyDoNotUseInAppConf.*",
  "views.html.*",
  "uk.gov.hmrc.*",
  "prod.*",
  "repositories.*"
)

  lazy val scoverageSettings = {
    Seq(
      ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
      ScoverageKeys.coverageMinimum := 80,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(plugins: _*)
    .settings(
      defaultSettings(),
      scalaSettings,
      publishingSettings,
      scoverageSettings,
      majorVersion := 3,
      libraryDependencies ++= AppDependencies.all,
      parallelExecution in Test := false,
      fork in Test := false,
      retrieveManaged := true,
      PlayKeys.playDefaultPort := 9942,
      routesGenerator := InjectedRoutesGenerator,
      resolvers += Resolver.bintrayRepo("hmrc", "releases"),
      resolvers += Resolver.jcenterRepo
    )
    .settings(
      scalacOptions ++= List(
        "-Yrangepos",
        "-Xlint:-missing-interpolator,_",
        "-Yno-adapted-args",
        "-feature",
        "-unchecked",
        "-language:implicitConversions",
        "-P:silencer:pathFilters=views;routes;TestStorage"
    ))
    .settings(scalaVersion := "2.12.11")
