import sbt.Keys._

val commonSettings =
  Seq(
    name := "wsr",
    version := "1.0",
    scalaVersion := "2.11.8",
    organization := "pl.touk.wsr",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions := Seq(
      "-target:jvm-1.7", "-unchecked", "-deprecation", "-encoding", "utf8", "-Xcheckinit", "-Xfatal-warnings", "-feature"
    ),
    parallelExecution in Test := false
  )

val scalaTestV        = "3.0.0"

lazy val commons = project.in(file("commons"))
  .settings(commonSettings)
  .settings(
    name := "wsr-commons",
    libraryDependencies ++= {
      val akkaV             = "2.4.10"
      val scalaLoggingV     = "3.4.0"

      Seq(
        "com.typesafe.akka"                   %% "akka-actor"                   % akkaV,
        "com.typesafe.scala-logging"          %% "scala-logging"                % scalaLoggingV,
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    }
  )

lazy val writer = project.in(file("writer"))
  .settings(commonSettings)
  .settings(
    name := "wsr-writer",
    libraryDependencies ++= { Seq.empty[ModuleID] }
  )
  .dependsOn(commons)
  .enablePlugins(SbtNativePackager, JavaServerAppPackaging)

lazy val reader = project.in(file("reader"))
  .settings(commonSettings)
  .settings(
    name := "wsr-reader",
    libraryDependencies ++= { Seq.empty[ModuleID] }
  )
  .dependsOn(commons)
  .enablePlugins(SbtNativePackager, JavaServerAppPackaging)

lazy val server = project.in(file("server"))
  .settings(commonSettings)
  .settings(
    name := "wsr-sever",
    libraryDependencies ++= { Seq.empty[ModuleID] }
  )
  .dependsOn(commons)
  .enablePlugins(SbtNativePackager, JavaServerAppPackaging)