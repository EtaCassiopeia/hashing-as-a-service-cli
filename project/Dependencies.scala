import sbt._

object Dependencies {

  object Versions {
    val akka = "2.6.6"
    val circe = "0.13.0"
    val logback = "1.2.3"
    val scalaTest = "3.2.0"
    val sttp = "2.2.0"
    val zio = "1.0.0-RC21"
    val zioLogging = "0.3.1"
    val pureConfig = "0.12.3"
    val mockServer = "5.10.0"
  }

  object Libraries {
    val akkaActor = "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka
    val zio = "dev.zio" %% "zio"                              % Versions.zio
    val pureConfig = "com.github.pureconfig" %% "pureconfig"  % Versions.pureConfig

    val circeModules: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras"
    ).map(_ % Versions.circe)

    val sttpModules = Seq(
      "com.softwaremill.sttp.client" %% "core"                          % Versions.sttp,
      "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % Versions.sttp,
      "com.softwaremill.sttp.client" %% "circe"                         % Versions.sttp
    )

    val zioLogging = "dev.zio" %% "zio-logging"            % Versions.zioLogging
    val zioLoggingSlf4j = "dev.zio" %% "zio-logging-slf4j" % Versions.zioLogging
    val logback = "ch.qos.logback"                         % "logback-classic" % Versions.logback

    val loggingModules = Seq(
      zioLogging,
      zioLoggingSlf4j,
      logback
    )

    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTest % Test

    lazy val mockServerModules: Seq[ModuleID] = Seq(
      "org.mock-server" % "mockserver-client-java" % Versions.mockServer,
      "org.mock-server" % "mockserver-netty"       % Versions.mockServer
    ).map(_ % Test)
  }
}
