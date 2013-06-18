package org.boringtechiestuff.spark

import spark.SparkContext
import java.util.Properties
import scala.collection.JavaConversions._
import java.io.FileInputStream

abstract class SparkApp extends App {

  val name = getClass.getName

  val local = args.toList contains ("--local")
  val emr = args.toList contains ("--emr")
  private val arguments = args.toList filter { !List("--local", "--emr").contains(_) }

  lazy val input = arguments(0)
  lazy val output = arguments(1)

  // TODO: Make master, install and library arguments to app.
  val (master, install, library, hdfsRoot) = (local, emr) match {
    case (true, _) => ("local", "", "target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar", "")
    case (_, true) =>
      // Read setup from properties file dropped by bootstrap
      val properties: Map[String, String] = {
        val properties = new Properties()
        properties.load(new FileInputStream("/home/hadoop/spark.properties"))
        properties.toMap
      }

      (
        properties("spark.master"),
        properties("spark.home"),
        "/home/hadoop/spark-assembly-1-SNAPSHOT.jar",
        properties("hdfs.root")
      )
  }

  def hdfs(path: String): String = hdfsRoot + path

  lazy val context = new SparkContext(master, name, install, Seq(library))
}