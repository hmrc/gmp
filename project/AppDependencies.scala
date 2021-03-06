import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "5.3.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-26" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-26",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.18.8-play26",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.4" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.4" % Provided cross CrossVersion.full,
    ws
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test: Seq[ModuleID] = ???
  }
  object Tests {
    def apply(): Seq[ModuleID] = new TestDependencies {
      override lazy val test: Seq[ModuleID] = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.10.0-play-26",
        "org.scalatest" %% "scalatest" % "3.0.8",
        "org.scalamock" %% "scalamock" % "3.6.0",
        "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3",
        "org.pegdown" % "pegdown" % "1.6.0",
        "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-26",
        "org.reactivemongo" %% "reactivemongo-iteratees" % "0.18.8",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.23",
        "org.mockito" % "mockito-all" % "1.10.19",
        "uk.gov.hmrc" %% "tax-year" % "1.3.0",
        "com.github.tomakehurst" % "wiremock-jre8" % "2.28.0",
        "uk.gov.hmrc" %% "bootstrap-play-26" % "4.0.0" % Test classifier "tests")
    }.test
  }

  val all: Seq[ModuleID] = compile ++ Tests()

}
