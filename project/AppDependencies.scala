import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playSuffix = "-play-30"
  private val bootstrapVersion = "8.4.0"
  private val hmrcMongoVersion = "1.7.0"

//  TODO: Sort out AppDependencies

  val compile = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-backend$playSuffix" % bootstrapVersion,
    "uk.gov.hmrc"            %% "tax-year"                % "3.0.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo$playSuffix"        % hmrcMongoVersion,
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "org.apache.pekko"      %% "pekko-testkit"            % "1.0.2",
    "org.mockito" %% "mockito-scala-scalatest" % "1.17.30",
    "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0",
    "uk.gov.hmrc" %% s"bootstrap-test$playSuffix" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test$playSuffix" % hmrcMongoVersion,
//    "org.scalatestplus.play" %% "scalatestplus-play"        % "5.1.0",
//    "org.scalatest"          %% "scalatest"                 % "3.2.9",
//    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test$playSuffix" % mongoVersion,
//    "org.scalamock"          %% "scalamock"               % "5.2.0",
//    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2",
//    "org.mockito"             % "mockito-all"             % "1.10.19",
//    "com.vladsch.flexmark"    % "flexmark-all"              % "0.35.10",
//    "com.github.tomakehurst"  % "wiremock-standalone"     % "2.27.2"
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
