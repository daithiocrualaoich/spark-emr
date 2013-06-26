package org.boringtechiestuff.spark

import org.boringtechiestuff.common.Strings
import java.io.FileInputStream
import java.util.Properties
import scala.collection.JavaConversions._
import spark.streaming.{ DStream, Seconds }

abstract class SparkApp extends App with Strings {

  val name = getClass.getName

  val local = args.toList contains "--local"
  val emr = !local

  val (master, install, libraries, hdfsRoot, awsAccessKey, awsSecretKey) = if (local) {
    (
      "local",
      "",
      Seq("target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar"),
      "",
      "",
      ""
    )
  } else {
    // Read setup from properties file dropped by bootstrap and run-spark script
    val properties: Map[String, String] = {
      val properties = new Properties()
      properties.load(new FileInputStream("/home/hadoop/spark.properties"))
      properties.toMap
    }

    (
      properties("spark.master"),
      properties("spark.home"),
      properties.get("spark.library").toSeq,
      properties("hdfs.root"),
      properties("aws.access.key"),
      properties("aws.secret.key")
    )
  }

  private val arguments = args.toList filter { _ != "--local" }

  // Add cluster HDFS prefix to input/output paths if necessary.
  // Also fixup S3 input paths here to have access key and secret needed for streaming.
  val S3UrlWithoutKeys = "s3n://([^@/]+/.*)".r
  lazy val input = arguments(0) match {
    case S3UrlWithoutKeys(path) =>
      "s3n://%s:%s@%s".format(
        awsAccessKey.urlencoded,
        awsSecretKey.urlencoded,
        path
      )
    case s3UrlWithKeys if s3UrlWithKeys.startsWith("s3n://") => arguments(0)
    case localPath if local => localPath
    case hdfsPath => hdfsRoot + hdfsPath
  }
  val output: String = if (arguments(1).startsWith("s3n://") || local) arguments(1) else hdfsRoot + arguments(1)

  lazy val context = {
    val context = new S3AwareSparkContext(master, name, install, libraries)

    context.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", awsAccessKey)
    context.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", awsSecretKey)

    context
  }
}

abstract class StreamingSparkApp extends SparkApp {
  val batchDurationSeconds: Long

  // Clean up old metadata after an hour SparkApp extends SparkApp {
  val cleanerTTL = 3600
  System.setProperty("spark.cleaner.ttl", cleanerTTL.toString)

  lazy val streamingContext = new S3AwareStreamingContext(context, Seconds(batchDurationSeconds))

  implicit def dStream2Collect[T: ClassManifest](stream: DStream[T]) = new {
    def collect[U: ClassManifest](f: PartialFunction[T, U]): DStream[U] = {
      stream.filter(f.isDefinedAt).map(f)
    }
  }
}
