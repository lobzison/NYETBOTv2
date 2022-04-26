resolvers += "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots"

enablePlugins(JavaServerAppPackaging)

lazy val commonSettings = Seq(
  scalaVersion := "3.1.2",
  organization := "io.github.lobzison",
  name         := "NYETBOTv2",
  version      := "0.1.0",
  libraryDependencies ++= Seq(
    // original library is "org.augustjune" %% "canoe" % "0.5.1",
    // but it's not released for scala3 and CE3
    // this is a fork of the original library with ScalaJs dropped
    "io.github.lobzison" %% "canoe"       % "0.1-SNAPSHOT",
    "org.tpolecat"       %% "skunk-core"  % "0.2.3",
    "org.tpolecat"       %% "skunk-circe" % "0.2.3",
    "com.github.geirolz" %% "fly4s-core"  % "0.0.12",
    "org.postgresql"      % "postgresql"  % "42.3.4"
  )
)

lazy val core =
    project
        .in(file("."))
        .settings(commonSettings*)
