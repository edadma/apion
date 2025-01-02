ThisBuild / licenses += "ISC"    -> url("https://opensource.org/licenses/ISC")
ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / evictionErrorLevel   := Level.Warn
ThisBuild / scalaVersion         := "3.6.2"
ThisBuild / organization         := "io.github.edadma"
ThisBuild / organizationName     := "edadma"
ThisBuild / organizationHomepage := Some(url("https://github.com/edadma"))
ThisBuild / version              := "0.0.2"

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"

ThisBuild / publishConfiguration := publishConfiguration.value.withOverwrite(true).withChecksums(Vector.empty)
ThisBuild / resolvers ++= Seq(
  Resolver.mavenLocal,
)
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Resolver.sonatypeOssRepos("releases")

ThisBuild / sonatypeProfileName := "io.github.edadma"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/edadma/apion"),
    "scm:git@github.com:edadma/apion.git",
  ),
)
ThisBuild / developers := List(
  Developer(
    id = "edadma",
    name = "Edward A. Maxedon, Sr.",
    email = "edadma@gmail.com",
    url = url("https://github.com/edadma"),
  ),
)

ThisBuild / homepage := Some(url("https://github.com/edadma/apion"))

ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
  ),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  scalaJSLinkerConfig ~= { _.withSourceMap(false) },
)

lazy val apion = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(nodejs)
  .settings(commonSettings)
  .settings(
    name := "apion",
    description := "A type-safe HTTP server framework for Scala.js that combines Express-style ergonomics with Scala's powerful type system",
    libraryDependencies ++= Seq(
      "org.scalatest"     %%% "scalatest"                   % "3.2.19" % "test",
      "com.lihaoyi"       %%% "pprint"                      % "0.9.0"  % "test",
      "io.github.edadma"  %%% "logger"                      % "0.0.6",
      "dev.zio"           %%% "zio-json"                    % "0.7.3",
      "org.scala-js"      %%% "scala-js-macrotask-executor" % "1.1.1",
      "io.github.cquiroz" %%% "scala-java-time"             % "2.6.0",
    ),
    jsEnv                           := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSUseMainModuleInitializer := true,
//    Test / scalaJSUseMainModuleInitializer := true,
//    Test / scalaJSUseTestModuleInitializer := false,
    Test / scalaJSUseMainModuleInitializer := false,
    Test / scalaJSUseTestModuleInitializer := true,
    publishMavenStyle                      := true,
    Test / publishArtifact                 := false,
  )

lazy val nodejs = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    name := "nodejs",
    description := "A minimal Scala.js facade library providing the Node.js bindings needed to support the Apion web framework",
    scalaJSUseMainModuleInitializer := true,
    publishMavenStyle               := true,
    Test / publishArtifact          := false,
  )

lazy val apion_root = project
  .in(file("."))
  .aggregate(apion, nodejs)
  .settings(
    publish / skip := true,
  )
