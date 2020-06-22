import Dependencies._
import Settings.commonSettings

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "hashing-as-a-service-cli",
    libraryDependencies ++= Seq(
      Libraries.akkaActor,
      Libraries.zio,
      Libraries.pureConfig,
      Libraries.scalatest
    ) ++ Libraries.circeModules
      ++ Libraries.sttpModules
      ++ Libraries.loggingModules
      ++ Libraries.mockServerModules,
    mainClass in Compile := Some("com.ing.hashservice.cli.Main"),
    mainClass in (Compile, packageBin) := Some("com.ing.hashservice.cli.Main"),
  )  .enablePlugins(JavaAppPackaging)