resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("play" % "sbt-plugin" % "2.0.3")

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" %% "scripted-plugin" % sv
}
