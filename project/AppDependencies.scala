import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "2.24.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-26" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-26",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.18.8-play26",
    "uk.gov.hmrc" %% "auth-client" % "3.0.0-play-26",
    ws
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }
  object Tests {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26",
        "org.scalatest" %% "scalatest" % "3.0.8",
        "org.scalamock" %% "scalamock" % "3.6.0",
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2",
        "org.pegdown" % "pegdown" % "1.6.0",
        "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26",
        "org.reactivemongo" %% "reactivemongo-iteratees" % "0.18.8",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.23",
        "org.mockito" % "mockito-all" % "1.10.19",
        "uk.gov.hmrc" %% "tax-year" % "1.1.0",
        "com.github.tomakehurst" % "wiremock-jre8" % "2.26.3",
        "uk.gov.hmrc" %% "bootstrap-play-26" % "1.14.0" % Test classifier "tests")
    }.test
  }

  val all: Seq[ModuleID] = compile ++ Tests()

}
