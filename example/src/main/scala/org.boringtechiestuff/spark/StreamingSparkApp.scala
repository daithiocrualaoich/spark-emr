package org.boringtechiestuff.spark

import spark.streaming.{ Duration, DStream, Seconds, StreamingContext }
import spark.SparkContext

class S3AwareStreamingContext(sparkContext: SparkContext, batchDuration: Duration)
    extends StreamingContext(sparkContext, batchDuration) {

  // TODO: Implement the equivalent of S3AwareSparkContext.textFile here
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
