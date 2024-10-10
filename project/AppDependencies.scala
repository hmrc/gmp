import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-30"
  private val bootstrapVersion = "9.5.0"
  private val hmrcMongoVersion = "2.2.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "tax-year"                        % "5.0.0",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.17" cross CrossVersion.full),
    "com.github.ghik"         % "silencer-lib" % "1.7.17" % Provided cross CrossVersion.full
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion,
    "org.scalatestplus.play" %% "scalatestplus-play"            % "7.0.1",
    "org.scalatestplus"      %% "scalacheck-1-17"               % "3.2.18.0",
    "org.mockito"            %% "mockito-scala-scalatest"       % "1.17.37",
    "org.apache.pekko"       %% "pekko-testkit"                 % "1.0.3",
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test

}
