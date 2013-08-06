#!/bin/bash -ev

mkdir -p target/spark-0.7.3-hadoop103
cd target/spark-0.7.3-hadoop103

if [ ! -f spark ]; then
  if [ ! -f spark-0.7.3-sources.tgz ]; then
    # wget http://spark-project.org/files/spark-0.7.3-sources.tgz
    curl -LO http://spark-project.org/files/spark-0.7.3-sources.tgz
  fi

  tar -xvzf spark-0.7.3-sources.tgz
  mv spark-0.7.3 spark
fi


cd spark

# We want to build against Hadoop 1.0.3
# sed -i 's/val HADOOP_VERSION = "1.0.4"/val HADOOP_VERSION = "1.0.3"/g' project/SparkBuild.scala
sed 's/val HADOOP_VERSION = "1.0.4"/val HADOOP_VERSION = "1.0.3"/g' project/SparkBuild.scala > tmp && mv tmp project/SparkBuild.scala

./sbt/sbt package
cd ..

rm -fr spark-0.7.3-hadoop103.tgz
tar -cvzf spark-0.7.3-hadoop103.tgz spark
