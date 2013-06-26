#!/bin/bash

cd /home/hadoop/

# Setup s3cmd for downloading jar
AWS_ACCESS_KEY=$(grep fs.s3.awsAccessKeyId /home/hadoop/conf/core-site.xml | sed 's/.*<value>//g' | sed 's/<\/value>.*//g')
AWS_SECRET_KEY=$(grep fs.s3.awsSecretAccessKey /home/hadoop/conf/core-site.xml | sed 's/.*<value>//g' | sed 's/<\/value>.*//g')

cat > /home/hadoop/.s3cfg <<EOF
[default]
access_key = $AWS_ACCESS_KEY
secret_key = $AWS_SECRET_KEY
EOF

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