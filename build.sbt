resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  scalaVersion := "3.3.1",
  organization := "io.github.lobzison",
  name         := "NYETBOTv2",
  version      := "0.1.1",
  packCopyDependenciesUseSymbolicLinks := false,
  libraryDependencies ++= Seq(
    "org.augustjune"     %% "canoe"         % "0.6.0",
    "co.fs2"             %% "fs2-core"      % "3.9.2",
    "org.tpolecat"       %% "skunk-core"    % "1.0.0-M1",
    "org.tpolecat"       %% "skunk-circe"   % "1.0.0-M1",
    "com.github.geirolz" %% "fly4s-core"    % "0.0.19",
    "org.postgresql"      % "postgresql"    % "42.6.0",
    "com.donderom"       %% "llm4s"         % "0.10.0",
    "io.circe"           %% "circe-literal" % "0.14.6"
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings*)
        .enablePlugins(PackPlugin)
