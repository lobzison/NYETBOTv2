resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  scalaVersion      := "3.5.2",
  organization      := "io.github.lobzison",
  name              := "NYETBOTv2",
  version           := "0.1.1",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions ++= Seq("-Xmax-inlines", "50"),
  libraryDependencies ++= Seq(
    // original library is "org.augustjune" %% "canoe" % "0.5.1",
    // this is a fork of the original library with ScalaJs dropped
    // because I'm dubm and dont know how to disable ScalaJs and it messes with sbt-pack/sbt-assembley
    "io.github.lobzison" %% "canoe"         % "0.1-SNAPSHOT",
    // "org.augustjune"     %% "canoe"         % "0.6.0",
    "co.fs2"             %% "fs2-core"      % "3.11.0",
    "org.tpolecat"       %% "skunk-core"    % "1.0.0-M7",
    "org.tpolecat"       %% "skunk-circe"   % "1.0.0-M7",
    "com.github.geirolz" %% "fly4s"         % "1.0.9",
    "org.postgresql"      % "postgresql"    % "42.7.4",
    "io.circe"           %% "circe-literal" % "0.14.6"
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings *)
        .enablePlugins(PackPlugin)
