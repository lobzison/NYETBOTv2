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
    "org.augustjune"     %% "canoe"                      % "0.1-SNAPSHOT",
//    "org.augustjune"     %% "canoe"                      % "0.6.0",
    "co.fs2"             %% "fs2-core"                   % "3.11.0",
    "org.tpolecat"       %% "skunk-core"                 % "1.1.0-M2",
    "org.tpolecat"       %% "skunk-circe"                % "1.1.0-M2",
    "com.github.geirolz" %% "fly4s"                      % "1.0.9",
    "org.flywaydb"        % "flyway-database-postgresql" % "10.14.0",
    "org.postgresql"      % "postgresql"                 % "42.7.4",
    "io.circe"           %% "circe-literal"              % "0.14.7"
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings *)
        .enablePlugins(PackPlugin)
