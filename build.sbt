ThisBuild / licenses += "ISC" -> url("https://opensource.org/licenses/ISC")
ThisBuild / versionScheme     := Some("semver-spec")

publish / skip := true

lazy val scalajs_template = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
//  .enablePlugins(ScalablyTypedConverterPlugin)
  .settings(
    name             := "scalajs-template",
    version          := "0.0.1",
    scalaVersion     := "3.6.2",
    organization     := "io.github.edadma",
    githubOwner      := "edadma",
    githubRepository := name.value,
//    libraryDependencies += "com.raquo" %%% "laminar" % "16.0.0",
//    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % "test",
//    libraryDependencies += "com.lihaoyi" %%% "pprint" % "0.9.0" % "test",
//    Compile / npmDependencies ++= Seq(
//      "socket.io" -> "4.7.3",
//    ),
    jsEnv                                  := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    Test / scalaJSUseMainModuleInitializer := true,
    Test / scalaJSUseTestModuleInitializer := false,
//    Test / scalaJSUseMainModuleInitializer := false,
//    Test / scalaJSUseTestModuleInitializer := true,
    scalaJSUseMainModuleInitializer := true,
//    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    publishMavenStyle      := true,
    Test / publishArtifact := false,
    licenses += "ISC"      -> url("https://opensource.org/licenses/ISC"),
  )
