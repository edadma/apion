ThisBuild / licenses += "ISC"  -> url("https://opensource.org/licenses/ISC")
ThisBuild / versionScheme      := Some("semver-spec")
ThisBuild / evictionErrorLevel := Level.Warn
ThisBuild / scalaVersion       := "3.6.2"
ThisBuild / organization       := "io.github.edadma"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
  ),
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  scalaJSLinkerConfig ~= { _.withSourceMap(false) },
  githubOwner      := "edadma",
  githubRepository := "apion",
)

lazy val apion = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(nodejs)
  .settings(commonSettings)
  .settings(
    name    := "apion",
    version := "0.0.1",
    libraryDependencies ++= Seq(
      "org.scalatest"    %%% "scalatest"                   % "3.2.19" % "test",
      "com.lihaoyi"      %%% "pprint"                      % "0.9.0"  % "test",
      "io.github.edadma" %%% "logger"                      % "0.0.5",
      "dev.zio"          %%% "zio-json"                    % "0.7.3",
      "org.scala-js"     %%% "scala-js-macrotask-executor" % "1.1.1",
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
    name                            := "nodejs",
    version                         := "0.0.1",
    scalaJSUseMainModuleInitializer := true,
    publishMavenStyle               := true,
    Test / publishArtifact          := false,
  )

lazy val apion_root = project
  .in(file("."))
  .aggregate(apion, nodejs)
  .settings(
    publish      := {},
    publishLocal := {},
  )
