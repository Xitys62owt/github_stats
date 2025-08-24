ThisBuild / scalaVersion := "3.3.1"

lazy val root = (project in file("."))
  .settings(
    name := "github_stats",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
      "io.circe" %% "circe-parser" % "0.14.5"
    )
  )