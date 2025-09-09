import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-30"
  private val bootstrapVersion = "10.1.0"
  private val hmrcMongoVersion = "2.7.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "tax-year"                        % "5.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.scalatestplus"      %% "mockito-4-11"                  % "3.2.17.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion,
    "org.scalatestplus.play" %% "scalatestplus-play"            % "7.0.1",
    "org.scalatestplus"      %% "scalacheck-1-17"               % "3.2.18.0",
    "org.apache.pekko"       %% "pekko-testkit"                 % "1.0.3",
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
