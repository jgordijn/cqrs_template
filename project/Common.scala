import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import sbt.Keys._

object Common {

  val settings =
    scalariformSettings ++List(
      // Core settings
      name := "cqrs_template",
      organization := "nl.trivento",
      version := "1.0.0",
      scalaVersion := Version.scala,
      ScalariformKeys.preferences := ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(AlignParameters, true)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(RewriteArrowSymbols, true)
    )
}
