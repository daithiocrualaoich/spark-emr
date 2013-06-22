#!/bin/bash

# Based on: https://elasticmapreduce.s3.amazonaws.com/samples/spark/0.7/install-spark-shark.sh

cd /home/hadoop/

# Install Scala and Spark
wget http://www.scala-lang.org/downloads/distrib/files/scala-2.9.3.tgz
wget https://dl.dropboxusercontent.com/u/1577066/spark/spark-0.7.2-hadoop103.tgz

tar -xvzf scala-2.9.3.tgz
tar -xvzf spark-0.7.2-hadoop103.tgz

export SCALA_HOME=/home/hadoop/scala-2.9.3

# Spark configuration
MASTER=$(grep -i "job.tracker<" /home/hadoop/conf/mapred-site.xml | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}')
SPACE=$(mount | grep mnt | awk '{print $3"/spark/"}' | xargs | sed 's/ /,/g')

# We need the internal domainname of the master for starting Spark on slaves and
# for making SparkContext objects in code.
MASTER_DNS=$(host $MASTER | sed 's/^.* //g' | sed 's/\.$//g')

cat > /home/hadoop/spark/conf/spark-env.sh <<EOF
# Spark configuration variables
export SCALA_HOME=/home/hadoop/scala-2.9.3
export MASTER=spark://$MASTER_DNS:7077
export SPARK_LIBRARY_PATH=/home/hadoop/native/Linux-amd64-64
export SPARK_JAVA_OPTS="-verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Dspark.local.dir=$SPACE"
EOF

# Drop the useful properties in a file for use in configuring Spark application in code.
AWS_ACCESS_KEY=$(grep fs.s3.awsAccessKeyId /home/hadoop/conf/core-site.xml | sed 's/.*<value>//g' | sed 's/<\/value>.*//g')
AWS_SECRET_KEY=$(grep fs.s3.awsSecretAccessKey /home/hadoop/conf/core-site.xml | sed 's/.*<value>//g' | sed 's/<\/value>.*//g')

cat > /home/hadoop/spark.properties <<EOF
spark.master=spark://$MASTER_DNS:7077
spark.home=/home/hadoop/spark
hdfs.root=hdfs://$MASTER:9000
aws.access.key=$AWS_ACCESS_KEY
aws.secret.key=$AWS_SECRET_KEY
EOF

# Install Hadoop libraries and configuration in Spark
cp /home/hadoop/lib/gson-* /home/hadoop/spark/lib_managed/jars/
cp /home/hadoop/lib/aws-java-sdk-* /home/hadoop/spark/lib_managed/jars/
cp /home/hadoop/conf/core-site.xml /home/hadoop/spark/conf/
cp /home/hadoop/hadoop-core.jar /home/hadoop/spark/lib_managed/jars/hadoop-core-1.0.3.jar 
cp /home/hadoop/lib/emr-metrics* /home/hadoop/spark/lib_managed/jars/

# Start master/slave Spark instance
grep -Fq '"isMaster":true' /mnt/var/lib/info/instance.json
if [ $? -eq 0 ];
then
  /home/hadoop/spark/bin/start-master.sh
else
  nc -z $MASTER 7077
  # Retry connection to master until successful
  while [ $? -eq 1 ]; do
    sleep 20
    nc -z  $MASTER 7077
  done

  /home/hadoop/spark/bin/start-slave.sh 1 spark://$MASTER_DNS:7077
fi
