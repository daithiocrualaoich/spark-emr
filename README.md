Spark on Elastic MapReduce
==========================

Takes this [article](http://aws.amazon.com/articles/4926593393724923) on running
Spark on Elastic MapReduce and explores making it a usable workflow for ad hoc
Spark programmes.

See [SparkEMRBootstrap](https://github.com/ianoc/SparkEMRBootstrap) also for
similar work.


Notes
-----

### 0.7-SNAPSHOT Version of Spark
The version of Spark deployed in the bootstrap used in the article above is a
0.7-SNAPSHOT. This is awkward for dependency management and anyway, Spark has
moved on to 0.7.2.

The `build-spark-0.7.2-hadoop103.sh` script here builds a replacement binary
tarball for the one used in the article. This build is of the most recent Spark
and made against the version of Hadoop current in Elastic MapReduce, i.e.
1.0.3.

You can find a copy of this tarball
[here](https://dl.dropboxusercontent.com/u/1577066/spark/spark-0.7.2-hadoop103.tgz).
This is the tarball used by `install-spark-0.7.2-bootstrap.sh`.

If you are inclined to paranoia, build your own `spark-0.7.2-hadoop103.tgz` and
replace the wget in `install-spark-0.7.2-bootstrap.sh`.

### Scala 2.9.3 Required for Spark 0.7.2
The bootstrap script here also bumps the version of Scala.

### Spark Environment Properties Files
Even with an installed and functional Spark cluster on Elastic MapReduce, it is
still difficult to create the necessary `SparkContext` objects in code or
correctly access HDFS.

Spark is sensitive to whether IP address or DNS names are using in cluster
connection URLs. This requires a DNS lookup, etc. Similarly, HDFS must be
referenced in paths using the full `http://host:port/path` syntax.

In addition, the AWS access key and secret key are extracted from the on
instance Hadoop configuration and made available so that S3 input/output can
be made convenient with helper methods.

All these properties are placed at `/home/hadoop/spark.properties`.

### HDFS Permissions
Spark sometimes creates files in HDFS owned by the root user. This is a problem
because the superuser in HDFS on Elastic MapReduce is actually the user named
hadoop. So you may encounter problems where a directory is owned by the hadoop
user running the job and preventing root owned files from being created.

One way around this is to create an Elastic MapReduce cluster with a HDFS that
does not use permissions. This is unlikely to be an issue with short lived
Elastic Map Reduce clusters but you may want to consider an alternative if
you have a large persistent cluster.

To disable HDFS permissions, launch the cluster with a `configure-hadoop`
bootstrap and pass `--hdfs-key-value dfs.permissions=false` as
arguments.

    --bootstrap-action s3://elasticmapreduce/bootstrap-actions/configure-hadoop
    --args "--hdfs-key-value,dfs.permissions=false"

### S3 Filesystem
Using S3 for input/output works if you use `s3n://` prefixes. The necessary
access and secret keys are set from those taken from the Hadoop configuration.

If your input paths are S3 directories, you also need to add path filters to
your input locations to ignore the base directory files in S3. See
[here](https://groups.google.com/forum/#!topic/spark-users/flQ9pdiZ1b8)
for a discussion of the problem.

See `S3AwareSparkContext.textFile` for an example of how to set this up assuming
specified paths are directories and not files. File paths work without
modification.

### Running Spark Jars
Fatjar Spark applications must be copied up and run from the cluster explicitly.
There is no corresponding feature to initiating Jar Steps in the Elastic
MapReduce API.

This is somewhat difficult to implement as a Jar Step because the Hadoop 1.0.3
`RunJar` isolates the fatjar in a different classloader from the Hadoop classes.
This is a problem because Spark needs certain package default acccesses to
some Hadoop internals and achieves this by creating classes in the same
Hadoop namespaces. This does not work when the classes are in separate
classloaders.

It is relatively easy to include a run script instead, however, that can be
run using the script runner jar pattern already in Elastic MapReduce. S3Cmd and
the previously sneaked AWS key values are used to download the fatjar from
S3 so it is functionally as transparent as the jar step would have been.


Yet Unresolved
--------------
The Spark cluster boots and runs jobs but refinements are needed to make it
convenient for general use.

### Use S3 Filesystem with Streaming
Similar to `SparkApp.s3nText`, some code is required to add the right path
filters to ignore directory S3 files which cause problems.

Working example needed.

### Underutilisation?
One worker per worker node may not be using the full resources of the Elastic
MapReduce cluster.


Examples
--------
First build `spark-assembly-1-SNAPSHOT.jar` in `example/target/scala-2.9.3`
using:

    cd example
    ./sbt assembly

Upload `install-spark-0.7.2-bootstrap.sh` to S3 where you can access it from
the cluster you are creating.

### Start a Test Cluster
Create an Elastic MapReduce cluster with a key for ssh/scp access. You should
add two bootstrap actions:

* Action `s3://elasticmapreduce/bootstrap-actions/configure-hadoop` with
  arguments `--hdfs-key-value dfs.permissions=false`. These arguments may need
  to be comma delimited or formatted per the technique you use to create the
  cluster.
* Action: S3 path to the `install-spark-0.7.2-bootstrap.sh` you uploaded. No
  arguments.

A cluster like this can be created in eu-west1 using `TestSparkEMRCluster`:

    java -cp target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.TestSparkEMRCluster \
      <aws-access-key> <aws-secret-key> <ssh-key-pair> \
      <log-uri> <s3-path-to-spark-bootstrap>

Wait.

When the cluster has booted and completed installation, ssh on:

    ssh -i <pem-file> hadoop@<master-public-dns>

Check your cluster is all there using:

    lynx http://localhost:8080

You should expect one worker per worker node you specified in cluster
construction.

Now, scp up the example fatjar `spark-assembly-1-SNAPSHOT.jar` and the
`example/dev/sample.json` data file to `/home/hadoop/` on the master node.

    scp -i <pem-file> example/target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar hadoop@<master-public-dns>:/home/hadoop/
    scp -i <pem-file> example/dev/sample.json hadoop@<master-public-dns>:/home/hadoop/

Connect to the master and edit `/home/hadoop/spark.properties` and ensure the
`spark.library` property is present and set:

    spark.library=/home/hadoop/spark-assembly-1-SNAPSHOT.jar

This is necessary while we test the applications directly on the cluster without
launching using the `run-spark.sh` script which takes care of setting this
property value correctly.

### HDFS Batch Example
From the master run the following to insert the data file into HDFS:

    hadoop fs -mkdir /input
    hadoop fs -put /home/hadoop/sample.json /input

Now, run the fatjar with path arguments in HDFS.

    java -cp /home/hadoop/spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.TweetWordCount /input /output

When this completes, you can inspect the output using:

    hadoop fs -ls /output
    hadoop fs -text /output/part*

### S3 Batch Example
The `SparkContext` objects produced are S3 aware for certain operations, and in
particular they are S3 aware for the example above.

Upload `sample.json` to an S3 location, and run:

    java -cp /home/hadoop/spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.TweetWordCount \
      s3n://<input-bucket>/<input-path> \
      s3n://<output-bucket>/<output-path>

Note that `s3n://` prefixes are required. The input path has to be an S3
directory. Using the full path of `sample.json` will not work.

### HDFS Streaming Example
Spark also provides a streaming mode.

As in the batch example, run the fatjar but using the streaming code instead:

    java -cp /home/hadoop/spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.StreamingTweetWordCount /input /output

Whenever any files are placed in HDFS under `/input` they will be picked up
by Spark Streaming and processed with output in HDFS under `output` by
timestamp.

In another console:

    hadoop fs -put /home/hadoop/sample.json /input/sample2.json
    hadoop fs -put /home/hadoop/sample.json /input/sample3.json
    hadoop fs -put /home/hadoop/sample.json /input/sample4.json
    hadoop fs -lsr /output

And look for nonempty part files.

### TBD: S3 Streaming Example

### Remote Invocation Example
Upload the assembled fatjar and `run-spark.sh` to a location in S3. Then
locally you can start a test cluster and run a Spark task on it remotely
using:

    java -cp example/target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.TestSparkEMRJarStep \
      <aws-access-key> \
      <aws-secret-key> \
      <ec2-key-pair-name> \
      <s3-log-directory> \
      <s3-path-to-install-spark-0.7.2-bootstrap.sh> \
      <s3-path-to-run-spark.sh> \
      <s3-path-to-run-spark-assembly-1-SNAPSHOT.jar> \
      <s3n-input-path> <s3n-output-path>

Note, the input and output paths must be S3N paths, not S3 paths.

The test cluster has to be manually terminated. It can easily be set up to
automatically terminate on task completion, just in test we may be interested
in examining the cluster post completion.

### TBD Remote Streaming Invocation Example