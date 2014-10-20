import AssemblyKeys._

name := "io-latency-test"

organization := "com.gses.experiments"

scalaVersion := "2.11.2"

mainClass in assembly := Some("com.gses.experiments.latency.Main")

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"

libraryDependencies += "org.rogach" %% "scallop" % "0.9.5"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.3"

jarName in assembly := "io-latency-test.jar"

artifact in (Compile, assembly) ~= { art =>
  art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in (Compile, assembly), assembly)

credentials += Credentials("Sonatype Nexus Repository Manager", "nexus", "deployment", "deployment123")

publishTo := {
    val nexus = "http://nexus/"
    if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
    else
        Some("releases" at nexus + "content/repositories/releases")
}

aetherPublishSettings

assemblySettings
