resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

lazy val commonSettings = Seq(
  scalaVersion := "3.2.1",
  organization := "io.github.lobzison",
  name         := "NYETBOTv2",
  version      := "0.1.0",
  libraryDependencies ++= Seq(
    "org.augustjune"     %% "canoe"       % "0.6.0",
    "org.tpolecat"       %% "skunk-core"  % "0.3.2",
    "org.tpolecat"       %% "skunk-circe" % "0.3.2",
    "com.github.geirolz" %% "fly4s-core"  % "0.0.14",
    "org.postgresql"      % "postgresql"  % "42.5.0"
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings*)
