package org.boringtechiestuff.spark

import spark.{ RDD, SparkContext }
import spark.rdd.HadoopRDD
import java.util.Properties
import scala.collection.JavaConversions._
import java.io.FileInputStream
import org.apache.hadoop.fs.{ Path, PathFilter }
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapred.FileInputFormat
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapred.TextInputFormat

abstract class SparkApp extends App {

  val name = getClass.getName

  val local = args.toList contains ("--local")
  val emr = args.toList contains ("--emr")
  private val arguments = args.toList filter { !List("--local", "--emr").contains(_) }

  lazy val input = arguments(0)
  lazy val output = arguments(1)

  // TODO: Make master, install and library arguments to app.
  val (master, install, library, hdfsRoot, awsAccessKey, awsSecretKey) = if (local) {
    ("local", "", "target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar", "", "", "")
  } else {
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
      properties("hdfs.root"),
      properties("aws.access.key"),
      properties("aws.secret.key")
    )
  }

  def hdfs(path: String): String = hdfsRoot + path

  lazy val context = {
    val context = new SparkContext(master, name, install, Seq(library))

    context.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", awsAccessKey)
    context.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", awsSecretKey)

    context
  }

  def s3nText(path: String, minSplits: Int = 0): RDD[String] = {
    val conf = new JobConf(context.hadoopConfiguration)

    FileInputFormat.setInputPaths(conf, path)
    FileInputFormat.setInputPathFilter(conf, classOf[S3NFilter])

    val in = new HadoopRDD(
      context,
      conf,
      classOf[TextInputFormat],
      classOf[LongWritable],
      classOf[Text],
      minSplits
    )

    in.map(pair => pair._2.toString)
  }
}

// From: https://github.com/RayRacine/spark/blob/52fbb4d05bd94cd936eeff5d40cb388eeaed424d/core/src/main/scala/spark/fs/S3N.scala
class S3NFilter extends PathFilter {
  private var prefix: Option[String] = None

  /**
   * The first path in the callback from Hadoop is the base path.
   * Remember it and use to filter out the bogus path that S3N is gives later.
   * Assumes Hadoop honors this specific ordering.
   */
  def accept(path: Path): Boolean = {
    if (prefix.isEmpty)
      prefix = Some(path.toString)

    val spath = path.toString
    val s3nPath = spath.toLowerCase.startsWith("s3n:")
    !s3nPath || s3nPath && (!spath.endsWith(prefix.get))
  }
}