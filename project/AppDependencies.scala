
import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "microservice-bootstrap" % "10.6.0",
    "uk.gov.hmrc" %% "play-reactivemongo"     % "6.2.0",
    "uk.gov.hmrc" %% "auth-client"            % "2.22.0-play-25",
    ws
  )

  val test = Seq(
        "uk.gov.hmrc"             %% "microservice-bootstrap"   % "10.6.0",
        "org.scalatest"           %% "scalatest"                % "3.0.2",
        "org.scalatestplus.play"  %% "scalatestplus-play"       % "2.0.1",
        "org.pegdown"             %  "pegdown"                  % "1.6.0",
        "com.typesafe.play"       %% "play-test"                % PlayVersion.current,
        "uk.gov.hmrc"             %% "hmrctest"                 % "3.4.0-play-25",
        "uk.gov.hmrc"             %% "reactivemongo-test"       % "3.1.0",
        "org.mockito"             %  "mockito-core"             % "1.9.5"
      ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}
