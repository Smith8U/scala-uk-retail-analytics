ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "uk-retail-analytics",
    scalacOptions ++= Seq("-Wunused:all", "-deprecation", "-feature", "-Xfatal-warnings"),
    // S1-4: third-party CSV parsing library (tototoshi scala-csv)
    libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "2.0.0",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )