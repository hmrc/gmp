import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playSuffix = "-play-30"
  private val bootstrapVersion = "8.4.0"
  private val hmrcMongoVersion = "1.7.0"

  val compile = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-backend$playSuffix" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo$playSuffix"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "tax-year"                      % "4.0.0",
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test$playSuffix"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test$playSuffix" % hmrcMongoVersion,
    "org.scalatestplus.play" %% "scalatestplus-play"          % "7.0.0",
    "org.scalatestplus"      %% "scalacheck-1-17"             % "3.2.17.0",
    "org.mockito"            %% "mockito-scala-scalatest"     % "1.17.30",
    "org.apache.pekko"       %% "pekko-testkit"               % "1.0.2",
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
