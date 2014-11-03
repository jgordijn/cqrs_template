import sbt.Keys._
import sbt._

object Version {
  val akka         = "2.3.5"
  val logback      = "1.1.2"
  val scala        = "2.11.2"
  val scalaTest    = "2.2.1"
  val spray        = "1.3.1"
}

object Library {
  val akkaActor            = "com.typesafe.akka"      %% "akka-actor"                    % Version.akka
  val akkaSlf4j            = "com.typesafe.akka"      %% "akka-slf4j"                    % Version.akka
  val akkaPersistence      = "com.typesafe.akka"      %% "akka-persistence-experimental" % Version.akka
  val akkaCluster          = "com.typesafe.akka"      %% "akka-cluster"                  % Version.akka
  val akkaContrib          = "com.typesafe.akka"      %% "akka-contrib"                  % Version.akka
  val akkaTestkit          = "com.typesafe.akka"      %% "akka-testkit"                  % Version.akka
  val akkaMultiNodeTestkit = "com.typesafe.akka"      %% "akka-multi-node-testkit"       % Version.akka
  val spray                = "io.spray"               %% "spray-can"                     % Version.spray
  val sprayRouting         = "io.spray"               %% "spray-routing"                 % Version.spray
  val sprayJson            = "io.spray"               %% "spray-json"                    % "1.2.6"
  val sprayTestkit         = "io.spray"               %% "spray-testkit"                 % Version.spray
  val logbackClassic       = "ch.qos.logback"         %  "logback-classic"               % Version.logback
  val scalaTest            = "org.scalatest"          %% "scalatest"                     % Version.scalaTest
  val commonsIo            = "commons-io"             %  "commons-io"                    % "2.4"

}

object Dependencies {

  import Library._

  val resolvers = Seq(
      "Spray Repository"    at "http://repo.spray.io/"
  )

  val cqrsTemplate = List(
    akkaActor,
    akkaPersistence,
    akkaCluster,
    akkaContrib,
    akkaSlf4j,
    spray,
    sprayRouting,
    sprayJson,
    logbackClassic,
    akkaTestkit % "test",
    akkaMultiNodeTestkit % "test",
    sprayTestkit % "test",
    scalaTest   % "test",
    commonsIo   % "test"
  )
}
