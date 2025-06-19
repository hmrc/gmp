import scoverage._
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val appName: String = "gmp"

lazy val plugins: Seq[Plugins] = Seq(play.sbt.PlayScala, SbtDistributablesPlugin)

lazy val scoverageExcludePatterns = List(
  "<empty>",
  "app.*",
  "gmp.*",
  "config.*",
  "metrics.*",
  "testOnlyDoNotUseInAppConf.*",
  "views.html.*",
  "uk.gov.hmrc.*",
  "prod.*",
  "repositories.*"
)

  lazy val scoverageSettings = {
    Seq(
      ScoverageKeys.coverageExcludedPackages := scoverageExcludePatterns.mkString("", ";", ""),
      ScoverageKeys.coverageMinimumStmtTotal := 89,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true
    )
  }

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(plugins: _*)
    .settings(
      defaultSettings(),
      scalaSettings,
      publishingSettings,
      scoverageSettings,
      majorVersion := 3,
      libraryDependencies ++= AppDependencies.all,
      libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
      Test / parallelExecution := false,
      Test / fork := false,
      retrieveManaged := true,
      PlayKeys.playDefaultPort := 9942,
      routesGenerator := InjectedRoutesGenerator
    )
    .settings(
      scalacOptions ++= List(
        "-P:silencer:pathFilters=routes",
        "-P:silencer:globalFilters=Unused import",
        "-Yrangepos",
        "-Xlint:-missing-interpolator,_",
        "-feature",
        "-unchecked",
        "-language:implicitConversions",
    ))
    .settings(scalaVersion := "2.13.12")
