import sbt._

object Dependencies {

  object Versions {
    val akka = "2.6.6"
    val circe = "0.13.0"
    val logback = "1.2.3"
    val scalaTest = "3.0.8"
    val sttp = "2.2.0"
  }

  object Libraries {
    val akkaActor = "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka

    val circeModules: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras"
    ).map(_ % Versions.circe)

    val sttpModules = Seq(
      "com.softwaremill.sttp.client" %% "core"              % Versions.sttp,
      "com.softwaremill.sttp.client" %% "akka-http-backend" % Versions.sttp,
      "com.softwaremill.sttp.client" %% "circe"             % Versions.sttp
    )

    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % Test
  }
}
