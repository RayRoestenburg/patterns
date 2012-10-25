package sectioncontrol

import sbt._
import sbt.Keys._
import com.typesafe.sbtscalariform.ScalariformPlugin
import com.typesafe.sbtscalariform.ScalariformPlugin.ScalariformKeys

object PatternsBuild extends Build {

  lazy val buildSettings = Seq(
    organization := "com.xebia",
    version      := "0.1-SNAPSHOT",
    scalaVersion := "2.10.0-RC3"
  )

  lazy val patterns = Project(
    id = "patterns",
    base = file("."),
    settings = defaultSettings ++ Seq(
      autoCompilerPlugins := true,
      fullClasspath in doc in Compile <<= fullClasspath in Compile,
      libraryDependencies ++=Dependencies.patterns
    )
  )

  override lazy val settings = super.settings ++ buildSettings

  lazy val baseSettings = Defaults.defaultSettings

  lazy val defaultSettings = baseSettings ++ formatSettings ++ Seq(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "OSS Sonatype" at "https://oss.sonatype.org/content/repositories/releases/", // for RC1 release
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked") ++ (
      if (true || (System getProperty "java.runtime.version" startsWith "1.7")) Seq() else Seq("-optimize")), // -optimize fails with jdk7
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),

    ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet
  )

  lazy val formatSettings = ScalariformPlugin.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
  }
}

// Dependencies

object Dependencies {
  import Dependency._
  val patterns = Seq(akkaActor, akkaKernel, akkaRemote, akkaCamel, protobuf, slf4jApi, Test.junit, Test.scalatest, Test.akkaTestKit)
}

object Dependency {
  
  // Versions
  object V {
    val Scalatest    = "1.8-B1"
    val Slf4j        = "1.6.4"
    val Akka         = "2.1.0-RC3"
  }

  // Compile
  val commonsCodec  = "commons-codec"               % "commons-codec"           % "1.4"        // ApacheV2
  val commonsIo     = "commons-io"                  % "commons-io"              % "2.0.1"      // ApacheV2
  val commonsNet    = "commons-net"                 % "commons-net"             % "3.1"        // ApacheV2
  val slf4jApi      = "org.slf4j"                   % "slf4j-api"               % V.Slf4j      // MIT
  val slf4jLog4j    = "org.slf4j"                   % "slf4j-log4j12"           % V.Slf4j      // MIT
  val akkaActor     = "com.typesafe.akka"           % "akka-actor_2.10.0-RC3"   % V.Akka       //ApacheV2
  val akkaKernel    = "com.typesafe.akka"           % "akka-kernel_2.10.0-RC3"  % V.Akka       //ApacheV2
  val akkaRemote    = "com.typesafe.akka"           % "akka-remote_2.10.0-RC3"  % V.Akka       //ApacheV2
  val akkaCamel     = "com.typesafe.akka"           % "akka-camel_2.10.0-RC3"   % V.Akka       exclude ("org.slf4j", "slf4j-api") //ApacheV2
  val protobuf      = "com.google.protobuf"        % "protobuf-java"           % "2.4.1"      // New BSD
  val scalatest     = "org.scalatest"               %% "scalatest"              % V.Scalatest  // ApacheV2
  object Test {
    val junit       = "junit"                       % "junit"                   % "4.5"        % "test" // Common Public License 1.0
    val scalatest   = "org.scalatest"               % "scalatest_2.10.0-RC3"    % V.Scalatest  % "test" // ApacheV2
    val akkaTestKit = "com.typesafe.akka"           % "akka-testkit_2.10.0-RC3" % V.Akka       % "test" // ApacheV2
  }
}
