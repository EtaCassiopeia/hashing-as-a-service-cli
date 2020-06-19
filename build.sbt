import Dependencies._
import Settings.commonSettings

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "hashing-as-a-service-cli",
    libraryDependencies ++= Seq(
      Libraries.akkaActor,
      Libraries.logback,
      Libraries.scalatest
    ) ++ Libraries.circeModules ++ Libraries.sttpModules
  )