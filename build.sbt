lazy val akkaHttpVersion = "10.5.3"
lazy val akkaVersion = "2.8.6"
lazy val circeVersion = "0.14.9"

scalaVersion := "2.13.14"
name := "minibank"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
//  "com.typesafe.akk" %% "akka-coordination" % "2.8.5",
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.datastax.oss" % "java-driver-core" % "4.17.0", // See https://github.com/akka/alpakka/issues/2556
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.1.1",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
  "ch.qos.logback" % "logback-classic" % "1.5.6",

  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)
