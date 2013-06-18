Spark on Elastic MapReduce
==========================

Takes this [article](http://aws.amazon.com/articles/4926593393724923) on running
Spark on Elastic MapReduce and explores making it a usable workflow.

See [SparkEMRBootstrap](https://github.com/ianoc/SparkEMRBootstrap) also for
similar type work.


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

For convenience the bootstrap script places the correct cluster connection path
and HDFS root in a properties file at `/home/hadoop/spark.properties`.

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


Yet Unresolved
--------------
The Spark cluster boots and runs jobs but refinements are needed to make it
convenient for general use.

### S3 Filesystem
Reading from S3 works if you use `s3n://` prefixes and set the necessary keys,
e.g. using:

    context.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", value)
    context.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", value)

Writing to S3 hasn't worked so far and a working example is needed.

For now,
(S3DistCp](http://docs.aws.amazon.com/ElasticMapReduce/latest/DeveloperGuide/UsingEMR_s3distcp.html)
can be used to load and retrieve data from the cluster HDFS instead and HDFS
paths used in Spark.

### Jar Step
Using an Elastic MapReduce jar step to run Spark programmes would likely just
work. However, the fatjar needed must be loaded somewhere accessible to the
running Spark application first. At the moment, the workflow is to scp the
built jar to the master node of the cluster and initiate Spark from there.

### Spark Streaming
The Spark Streaming example hasn't been tested yet. Probably works.

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

Create an Elastic MapReduce cluster with a key for ssh/scp access. You should
add two bootstrap actions:

* Action `s3://elasticmapreduce/bootstrap-actions/configure-hadoop` with
  arguments `--hdfs-key-value dfs.permissions=false`. These arguments may need
  to be comma delimited or formatted per the technique you use to create the
  cluster.
* Action: S3 path to the `install-spark-0.7.2-bootstrap.sh` you uploaded. No
  arguments.

(A cluster like this can be creating from the Elastic MapReduce console if you,
for instance, configure it as a test WordCount Streaming demo and mark the
option to leave the cluster running after completion. The demo completes very
quickly leaving you with a test Spark cluster.)

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

From the master run the following to insert the data file into HDFS:

    hadoop fs -mkdir /input
    hadoop fs -put /home/hadoop/sample.json /input

Now, run the fatjar with the `--emr` option and path arguments in HDFS.

    cd /home/hadoop
    java -cp spark-assembly-1-SNAPSHOT.jar \
      org.boringtechiestuff.spark.TweetWordCount --emr /input /output

When this completes, you can inspect the output using:

    hadoop fs -ls /output
    hadoop fs -text /output/part*
