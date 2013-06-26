#!/bin/bash

cd /home/hadoop/

s3cmd get --force "$1"
JAR=$(basename $1)

# Update spark.properties with Jar name
egrep -v '^spark.library=' spark.properties > spark.properties.new
cat >> spark.properties.new <<EOF
spark.library=/home/hadoop/$JAR
EOF
mv spark.properties.new spark.properties

shift 1
java -cp "$JAR" $@