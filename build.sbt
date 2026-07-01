lazy val commonSettings = Seq(
  scalaVersion      := "3.8.4",
  organization      := "io.github.lobzison",
  name              := "NYETBOTv2",
  version           := "0.1.1",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalacOptions ++= Seq("-Xmax-inlines", "50"),
  Compile / scalacOptions += "-Werror",
  Test / scalacOptions += "-Werror",
  libraryDependencies ++= Seq(
    // Forked canoe, pinned. Do not touch — its transitives are intentionally
    // overridden by the explicit versions below so the main project controls
    // its own dependency versions.
    "org.augustjune" %% "canoe" % "0.1-SNAPSHOT",

    // Direct dependencies
    "co.fs2"             %% "fs2-core"                   % "3.13.0",
    "org.tpolecat"       %% "skunk-core"                 % "1.1.0-RC1",
    "org.tpolecat"       %% "skunk-circe"                % "1.1.0-RC1",
    "com.github.geirolz" %% "fly4s"                      % "2.0.0",
    "org.flywaydb"        % "flyway-database-postgresql" % "12.10.0",
    "org.postgresql"      % "postgresql"                 % "42.7.12",

    // Promoted from canoe transitives — pinned directly so eviction picks ours
    // rather than canoe's older declared versions.
    "org.typelevel" %% "cats-core"           % "2.13.0",
    "org.typelevel" %% "cats-effect"         % "3.7.0",
    "io.circe"      %% "circe-core"          % "0.14.16",
    "io.circe"      %% "circe-generic"       % "0.14.16",
    "io.circe"      %% "circe-parser"        % "0.14.16",
    "io.circe"      %% "circe-literal"       % "0.14.16",
    "org.http4s"    %% "http4s-dsl"          % "0.23.34",
    "org.http4s"    %% "http4s-blaze-client" % "0.23.17",
    "org.http4s"    %% "http4s-blaze-server" % "0.23.17",
    "org.http4s"    %% "http4s-circe"        % "0.23.34",
    "org.typelevel" %% "log4cats-slf4j"      % "2.8.0",

    // Config file (HOCON) for tunable, non-secret parameters. Pure Java, no Scala
    // binary-version constraints.
    "com.typesafe" % "config" % "1.4.9",

    // Tests: munit + its cats-effect integration (no live Postgres/Ollama needed).
    "org.scalameta" %% "munit"             % "1.3.3" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.2.0" % Test,

    // Integration tests run against a real Postgres (skunk speaks the PG wire protocol and
    // cannot use SQLite). The darwin-arm64v8 binary is added explicitly because embedded-postgres
    // only bundles amd64 by default; linux-amd64 (CI) is bundled already.
    "io.zonky.test"          % "embedded-postgres"                         % "2.2.2"  % Test,
    "io.zonky.test.postgres" % "embedded-postgres-binaries-darwin-arm64v8" % "18.4.0" % Test
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings *)
        .enablePlugins(PackPlugin)
