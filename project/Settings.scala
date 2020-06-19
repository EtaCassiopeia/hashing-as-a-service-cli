import sbt.Keys._
import sbt._

object Settings {

  lazy val commonSettings =
    compilerSettings ++
      sbtSettings ++ Seq(
      organization := "com.ing"
    )

  lazy val compilerSettings =
    Seq(
      scalaVersion := "2.13.2",
      javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
    )

  lazy val sbtSettings =
    Seq(fork := true, cancelable in Global := true)
}
