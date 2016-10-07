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
    parallelExecution in Test := false,
    dockerRepository := Some("wsr")
  )

val akkaV             = "2.4.11"
val scalaTestV        = "3.0.0"

def entryPointArgs(name: String, debugPort: Int, jmxPort: Int) =
  Seq(
    s"bin/$name",
    "-jvm-debug", debugPort.toString,
    "-Dcom.sun.management.jmxremote",
    s"-Dcom.sun.management.jmxremote.port=$jmxPort",
    "-Dcom.sun.management.jmxremote.local.only=false",
    "-Dcom.sun.management.jmxremote.authenticate=false",
    "-Dcom.sun.management.jmxremote.ssl=false"
  )

lazy val commons = project.in(file("commons"))
  .settings(commonSettings)
  .settings(
    name := "wsr-commons",
    libraryDependencies ++= {
      val scalaLoggingV     = "3.4.0"
      val logbackV          = "1.1.7"

      Seq(
        "com.typesafe.akka"                   %% "akka-stream"                  % akkaV,
        "com.typesafe.akka"                   %% "akka-slf4j"                   % akkaV,
        "com.typesafe.scala-logging"          %% "scala-logging"                % scalaLoggingV,
        "ch.qos.logback"                       % "logback-classic"              % logbackV,
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    }
  )

lazy val writer = project.in(file("writer"))
  .settings(commonSettings)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "wsr-writer",
    assemblyJarName in assembly := "wsr-writer.jar",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    },
    dockerEntrypoint := entryPointArgs(name.value, 9991, 9981)
  )
  .dependsOn(commons)

lazy val reader = project.in(file("reader"))
  .settings(commonSettings)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "wsr-reader",
    assemblyJarName in assembly := "wsr-reader.jar",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    },
    dockerEntrypoint := entryPointArgs(name.value, 9993, 9983)
  )
  .dependsOn(commons)

lazy val server = project.in(file("server"))
  .settings(commonSettings)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "wsr-server",
    assemblyJarName in assembly := "wsr-server.jar",
    libraryDependencies ++= {
      val picklingV =              "0.10.1"
      Seq(
        "org.scala-lang.modules"              %% "scala-pickling"               % picklingV,
        "com.typesafe.akka"                   %% "akka-testkit"                 % akkaV % "test",
        "org.scalatest"                       %% "scalatest"                    % scalaTestV % "test"
      )
    },
    dockerEntrypoint := entryPointArgs(name.value, 9992, 9982)
  )
  .dependsOn(commons)

lazy val all = project.in(file("all"))
  .settings(commonSettings)
  .settings(
    name := "wsr-all",
    assemblyJarName in assembly := "wsr-all.jar",
    libraryDependencies ++= { Seq.empty[ModuleID] }
  )
  .dependsOn(writer, reader, server)