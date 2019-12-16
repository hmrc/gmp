
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "5.1.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-25" % bootstrapPlayVersion,
    // "uk.gov.hmrc" %% "bootstrap-play-26" % "1.1.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-25",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.17.1-play25",
    "uk.gov.hmrc" %% "auth-client"            % "2.26.0-play-25",
    ws
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "microservice-bootstrap"   % "10.6.0",
    "uk.gov.hmrc"             %% "hmrctest"                 % "3.9.0-play-25",
    "uk.gov.hmrc"             %% "reactivemongo-test"       % "4.15.0-play-25",
    "org.scalatest"           %% "scalatest"                % "3.0.8",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.1",
    "org.pegdown"             %  "pegdown"                  % "1.6.0",
    "com.typesafe.play"       %% "play-test"                % PlayVersion.current,
    "org.mockito"             %  "mockito-core"             % "3.0.0"
      ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}
