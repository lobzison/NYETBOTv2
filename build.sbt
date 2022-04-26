lazy val commonSettings = Seq(
  scalaVersion := "3.1.2",
  organization := "neytbot",
  version      := "0.1.0",
  libraryDependencies ++= Seq(
    "org.augustjune"     %% "canoe"       % "0.5.1+19-344d9218+20220424-1333-SNAPSHOT",
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
