import sbt._
import sbt.Keys._
import Defaults._

object PluginBuild extends Build {

  lazy val play2TypeScript = Project(
    id = "play2-typescript", base = file(".")
  ).settings(
    sbtPlugin := true,
    scalaVersion := "2.10.2",
    description := "SBT plugin for handling TypeScript assets in Play 2",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "1.9.1" % "test"
    ),
    resolvers += Resolver.url("Typesafe ivy releases", url("http://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns),
    resolvers += "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases",
//    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.0" % "provided"),
    libraryDependencies <+= (sbtVersion in sbtPlugin, sbtBinaryVersion in update, scalaBinaryVersion in update) {
      case ("0.12", sbtV, scalaV) =>
        sbtPluginExtra("play" % "sbt-plugin" % "2.1.5" % "provided", "0.12", "2.9.2").
          exclude("com.github.scala-incubator.io", "scala-io-core_2.9.1").
          exclude("com.github.scala-incubator.io", "scala-io-file_2.9.1")
      case ("0.13", sbtV, scalaV) =>
        sbtPluginExtra("com.typesafe.play" % "sbt-plugin" % "2.2.0" % "provided", "0.13", "2.10")
    },
    organization := "com.github.mumoshu",
    publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    version := "0.3.0-RC2-SNAPSHOT",
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <url>https://github.com/mumoshu/play2-typescript</url>
        <licenses>
          <license>
            <name>Apache v2 License</name>
            <url>https://github.com/mumoshu/play2-typescript/blob/master/LICENSE</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:mumoshu/play2-typescript.git</url>
          <connection>scm:git:git@github.com:mumoshu/play2-typescript.git</connection>
        </scm>
        <developers>
          <developer>
            <id>you</id>
            <name>KUOKA Yusuke</name>
            <url>https://github.com/mumoshu</url>
          </developer>
        </developers>
      ),
    // Without forking, we might get the error below while running 2.1.x-entrypoints:
    //   [error] [ERROR] Terminal initialization failed; falling back to unsupported
    //   ...
    //   java.lang.IncompatibleClassChangeError: JLine incompatibility detected.  Check that the sbt launcher is version 0.13.x or later.
    fork in ScriptedPlugin.scripted := true
  ).settings(CrossBuilding.scriptedSettings:_*).settings(
    SbtScriptedSupport.scriptedLaunchOpts ++= Seq("-XX:+CMSClassUnloadingEnabled", "-XX:MaxPermSize=256m", "-Xmx512M", "-Xss2M"),
    CrossBuilding.crossSbtVersions := Seq("0.12", "0.13")
  ).settings(net.virtualvoid.sbt.cross.CrossPlugin.crossBuildingSettings: _*)

}
