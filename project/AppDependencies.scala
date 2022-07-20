import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"

  private val hmrcMongoVersion = "0.65.0"

  val compile = Seq(
    "uk.gov.hmrc"         %% s"bootstrap-backend-$playVersion"    % "5.12.0",
    "uk.gov.hmrc.mongo"   %% s"hmrc-mongo-$playVersion"                 % hmrcMongoVersion ,
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full),
    "com.github.ghik"      % "silencer-lib" % "1.7.5"             % Provided cross CrossVersion.full,
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"          %% "scalatest"               % "3.2.9",
    "org.scalamock"          %% "scalamock"               % "5.1.0",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "org.reactivemongo"      %% "reactivemongo-iteratees" % "1.0.6",
    "com.typesafe.akka"      %% "akka-testkit"            % "2.6.14",
    "org.mockito"             % "mockito-all"             % "1.10.19",
    "uk.gov.hmrc"            %% "tax-year"                % "1.4.0",
    "com.github.tomakehurst"  % "wiremock-standalone"     % "2.27.1",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.35.10"
  )

  val all: Seq[ModuleID] = compile ++ test

}
