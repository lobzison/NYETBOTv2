lazy val commonSettings = Seq(
  scalaVersion      := "3.8.3",
  organization      := "io.github.lobzison",
  name              := "NYETBOTv2",
  version           := "0.1.1",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions ++= Seq("-Xmax-inlines", "50"),
  // sbt-tpolecat enables -Werror / -Xfatal-warnings by default. Pre-existing
  // unused-import and deprecation warnings surfaced by Scala 3.8 + newer skunk
  // would fail the build; cleaning them up is out of scope for the dep bump.
  Compile / scalacOptions --= Seq("-Werror", "-Xfatal-warnings"),
  libraryDependencies ++= Seq(
    // Forked canoe, pinned. Do not touch — its transitives are intentionally
    // overridden by the explicit versions below so the main project controls
    // its own dependency versions.
    "org.augustjune" %% "canoe" % "0.1-SNAPSHOT",

    // Direct dependencies
    "co.fs2"             %% "fs2-core"                   % "3.13.0",
    "org.tpolecat"       %% "skunk-core"                 % "1.1.0-RC1",
    "org.tpolecat"       %% "skunk-circe"                % "1.1.0-RC1",
    "com.github.geirolz" %% "fly4s"                      % "1.2.0",
    "org.flywaydb"        % "flyway-database-postgresql" % "11.8.2",
    "org.postgresql"      % "postgresql"                 % "42.7.10",

    // Promoted from canoe transitives — pinned directly so eviction picks ours
    // rather than canoe's older declared versions.
    "org.typelevel" %% "cats-core"           % "2.13.0",
    "org.typelevel" %% "cats-effect"         % "3.7.0",
    "io.circe"      %% "circe-core"          % "0.14.15",
    "io.circe"      %% "circe-generic"       % "0.14.15",
    "io.circe"      %% "circe-parser"        % "0.14.15",
    "io.circe"      %% "circe-literal"       % "0.14.15",
    "org.http4s"    %% "http4s-dsl"          % "0.23.17",
    "org.http4s"    %% "http4s-blaze-client" % "0.23.17",
    "org.http4s"    %% "http4s-blaze-server" % "0.23.17",
    "org.http4s"    %% "http4s-circe"        % "0.23.17",
    "org.typelevel" %% "log4cats-slf4j"      % "2.8.0",

    // Config file (HOCON) for tunable, non-secret parameters. Pure Java, no Scala
    // binary-version constraints.
    "com.typesafe" % "config" % "1.4.3",

    // Tests: munit + its cats-effect integration (no live Postgres/Ollama needed).
    "org.scalameta" %% "munit"             % "1.1.0" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings *)
        .enablePlugins(PackPlugin)
