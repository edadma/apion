---
title: Installation
description: How to add Apion to your Scala.js project
---

## Requirements

- **Scala 3.x** (tested with 3.8.3)
- **Scala.js 1.x** (tested with 1.21.0)
- **Node.js** (runtime)
- **sbt** (build tool)

## Add the Dependency

Add Apion to your `build.sbt`:

```scala
libraryDependencies += "io.github.edadma" %%% "apion" % "0.1.0"
```

## SBT Project Setup

A minimal `build.sbt` for a Scala.js project using Apion:

```scala
ThisBuild / scalaVersion := "3.8.3"

enablePlugins(ScalaJSPlugin)

scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }

libraryDependencies += "io.github.edadma" %%% "apion" % "0.1.0"
```

Also add the `sbt-scalajs` plugin in `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.21.0")
```

## Build & Run

```bash
# Compile and link
sbt fastLinkJS

# Run with Node.js
node target/scala-3.8.3/project-fastopt/main.js
```

For development with auto-restart, you can use `nodemon`:

```bash
npx nodemon --watch target -e js --exec "node target/scala-3.8.3/project-fastopt/main.js"
```
