import sbt.Keys._

val commonSettings =
  Seq(
    name := "wsr",
    version := "1.0",
    scalaVersion := "2.11.8",
    organization := "pl.touk.wsr",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions := Seq(
      "-target:jvm-1.8", "-unchecked", "-deprecation", "-encoding", "utf8", "-Xcheckinit", "-Xfatal-warnings", "-feature"
    ),
    parallelExecution in Test := false
  )

val akkaV             = "2.4.11"
val scalaTestV        = "3.0.0"

lazy val commons = project.in(file("commons"))
  .settings(commonSettings)
  .settings(
    name := "wsr-commons",
    libraryDependencies ++= {
      val scalaLoggingV     = "3.4.0"
      val logbackV          = "1.1.7"

      Seq(
        "com.typesafe.akka"                   %% "akka-actor"                   % akkaV,
        "com.typesafe.scala-logging"          %% "scala-logging"                % scalaLoggingV,
        "ch.qos.logback"                       % "logback-classic"              % logbackV,
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    }
  )

lazy val writer = project.in(file("writer"))
  .settings(commonSettings)
  .settings(
    name := "wsr-writer",
    assemblyJarName in assembly := "wsr-writer.jar",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    }
  )
  .dependsOn(commons)

lazy val reader = project.in(file("reader"))
  .settings(commonSettings)
  .settings(
    name := "wsr-reader",
    assemblyJarName in assembly := "wsr-reader.jar",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    }
  )
  .dependsOn(commons)

lazy val server = project.in(file("server"))
  .settings(commonSettings)
  .settings(
    name := "wsr-sever",
    assemblyJarName in assembly := "wsr-sever.jar",
    libraryDependencies ++= { Seq.empty[ModuleID] }
  )
  .dependsOn(commons)