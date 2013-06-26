package org.boringtechiestuff.spark

import spark.{ RDD, SparkContext }
import spark.rdd.HadoopRDD
import org.apache.hadoop.fs.{ Path, PathFilter }
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapred.{ InputFormat, FileInputFormat, JobConf, TextInputFormat }
import spark.streaming.{ StreamingContext, Duration }

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

class S3AwareSparkContext(
    override val master: String,
    override val appName: String,
    override val sparkHome: String = null,
    override val jars: Seq[String] = Nil,
    override val environment: Map[String, String] = Map()) extends SparkContext(master, appName, sparkHome, jars, environment) {

  override def textFile(path: String, minSplits: Int = defaultMinSplits): RDD[String] = path match {
    case _ if path.startsWith("s3n://") =>
      hadoopFileWithPathFilter(
        path, classOf[S3NFilter],
        classOf[TextInputFormat], classOf[LongWritable], classOf[Text],
        minSplits
      ).map(pair => pair._2.toString)

    case _ => super.textFile(path, minSplits)
  }

  def hadoopFileWithPathFilter[K, V](
    path: String, inputPathFilterClass: Class[_ <: PathFilter],
    inputFormatClass: Class[_ <: InputFormat[K, V]], keyClass: Class[K], valueClass: Class[V],
    minSplits: Int = defaultMinSplits): RDD[(K, V)] = {
    val conf = new JobConf(hadoopConfiguration)
    FileInputFormat.setInputPaths(conf, path)
    FileInputFormat.setInputPathFilter(conf, inputPathFilterClass)
    new HadoopRDD(this, conf, inputFormatClass, keyClass, valueClass, minSplits)
  }
}

class S3AwareStreamingContext(sparkContext: SparkContext, batchDuration: Duration)
    extends StreamingContext(sparkContext, batchDuration) {

  // TODO: Implement the equivalent of S3AwareSparkContext.textFile here
}