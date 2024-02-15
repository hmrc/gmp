resolvers += "HMRC Releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.jcenterRepo

addSbtPlugin("uk.gov.hmrc"       %  "sbt-auto-build"        % "3.20.0")
// TODO: REVERT PLAY 3.0 UPGRADE ONCE APP DEPENDENCIES MOVED TO CORRECT COMPILE/TEST
addSbtPlugin("org.playframework" %  "sbt-plugin"            % "3.0.1")
//addSbtPlugin("com.typesafe.play" %  "sbt-plugin"            % "2.8.19")
addSbtPlugin("uk.gov.hmrc"       %  "sbt-distributables"    % "2.5.0")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "1.0.0" exclude("org.scala-lang.modules", "scala-xml_2.12"))
addSbtPlugin("org.scoverage"     %  "sbt-scoverage"         % "2.0.9")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
