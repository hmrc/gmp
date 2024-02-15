import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playSuffix = "-play-30"

//  TODO: Sort out AppDependencies

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28" % "7.12.0",
    "uk.gov.hmrc"            %% "tax-year"                % "3.0.0",
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "com.typesafe.akka"      %% "akka-testkit"            % "2.6.20",
    "org.scalatestplus.play" %% "scalatestplus-play"        % "5.1.0"    % "test",
    "org.scalatest"          %% "scalatest"                 % "3.2.9"    % "test",
    "com.vladsch.flexmark"    % "flexmark-all"              % "0.35.10"  % "test",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % "0.74.0",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-28"        % "0.73.0"   % "test",
    "org.scalamock"          %% "scalamock"               % "5.2.0",
    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2" % "test",
    "org.mockito"             % "mockito-all"             % "1.10.19",
    "com.github.tomakehurst"  % "wiremock-standalone"     % "2.27.2"
  )
//  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
