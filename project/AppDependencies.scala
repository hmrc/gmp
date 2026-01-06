import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-30"
  private val bootstrapVersion = "10.5.0"
  private val hmrcMongoVersion = "2.11.0"
  private val pekkoVersion = "1.2.1"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "tax-year"                        % "6.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.scalatestplus"      %% "mockito-4-11"                  % "3.2.18.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion,
    "org.scalatestplus.play" %% "scalatestplus-play"            % "7.0.2",
    "org.scalatestplus"      %% "scalacheck-1-17"               % "3.2.18.0",
    "org.apache.pekko"       %% "pekko-http"                    % "1.3.0",
    "org.apache.pekko"       %% "pekko-actor-typed"             % pekkoVersion,
    "org.apache.pekko"       %% "pekko-stream"                  % pekkoVersion,
    "org.apache.pekko"       %% "pekko-serialization-jackson"   % pekkoVersion,
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
