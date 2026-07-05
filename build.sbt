import xerial.sbt.Sonatype.sonatypeCentralHost

ThisBuild / licenses               := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))
ThisBuild / versionScheme          := Some("semver-spec")
ThisBuild / evictionErrorLevel     := Level.Warn
ThisBuild / scalaVersion           := "3.8.3"
ThisBuild / organization           := "io.github.edadma"
ThisBuild / organizationName       := "edadma"
ThisBuild / organizationHomepage   := Some(url("https://github.com/edadma"))
ThisBuild / version                := "0.2.0"
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

ThisBuild / publishConfiguration := publishConfiguration.value.withOverwrite(true).withChecksums(Vector.empty)
ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots
ThisBuild / resolvers += Resolver.sonatypeCentralRepo("releases")

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

ThisBuild / publishTo := sonatypePublishToBundle.value

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Werror",
  ),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  scalaJSLinkerConfig ~= { _.withSourceMap(false) },
  publishMavenStyle      := true,
  Test / publishArtifact := false,
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
      "com.lihaoyi"       %%% "pprint"                      % "0.9.6"  % "test",
      "io.github.edadma"  %%% "logger"                      % "0.0.6",
      "dev.zio"           %%% "zio-json"                    % "0.9.0",
      "org.scala-js"      %%% "scala-js-macrotask-executor" % "1.1.1",
      "io.github.cquiroz" %%% "scala-java-time"             % "2.6.0",
    ),
    jsEnv                           := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSUseMainModuleInitializer := true,
//    Test / scalaJSUseMainModuleInitializer := true,
//    Test / scalaJSUseTestModuleInitializer := false,
    Test / scalaJSUseMainModuleInitializer := false,
    Test / scalaJSUseTestModuleInitializer := true,
  )

lazy val nodejs = project
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .settings(
    name := "nodejs",
    description := "A minimal Scala.js facade library providing the Node.js bindings needed to support the Apion web framework",
    scalaJSUseMainModuleInitializer := true,
  )

lazy val apion_root = project
  .in(file("."))
  .aggregate(apion, nodejs)
  .settings(
    name                := "apion",
    publish / skip      := true,
    publishLocal / skip := true,
  )
