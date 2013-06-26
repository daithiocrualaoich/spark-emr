package org.boringtechiestuff.spark

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClient
import com.amazonaws.services.elasticmapreduce.model._
import com.amazonaws.services.elasticmapreduce.util.{ StepFactory, BootstrapActions }
import scala.collection.JavaConversions._

/**
 * Commandline job to start a Spark MapReduce Cluster in AWS eu-west-1 region.
 *
 * Usage:
 *  java -cp target/scala-2.9.3/spark-assembly-1-SNAPSHOT.jar \
 *     org.boringtechiestuff.spark.TestSparkEMRJarStep \
 *     <aws-access-key> <aws-secret-key> <ssh-key-pair> \
 *     <log-uri> \
 *     <s3-path-to-spark-bootstrap> <s3-path-to-run-spark-script> \
 *     <s3-path-to-uploaded-assembly-jar> \
 *     <s3n-input-path> <s3n-output-path>
 */
object TestSparkEMRJarStep extends App {

  val name = "Spark"

  val Array(
    accessKey, secretKey, sshKeyPair, logUri,
    installSparkBootstrap, runSpark, assembly,
    input, output
    ) = args

  val emr: AmazonElasticMapReduceClient = {
    val credentials = new BasicAWSCredentials(accessKey, secretKey)
    val client = new AmazonElasticMapReduceClient(credentials)
    client.setEndpoint("elasticmapreduce.eu-west-1.amazonaws.com")

    client
  }

  val instances = new JobFlowInstancesConfig()
    .withEc2KeyName(sshKeyPair)
    .withHadoopVersion("1.0.3")
    .withInstanceCount(2)
    .withKeepJobFlowAliveWhenNoSteps(true)
    .withMasterInstanceType("m1.large")
    .withSlaveInstanceType("m1.large")

  val bootstrapActions = new BootstrapActions

  val relaxHdfsPermissions = bootstrapActions.newConfigureHadoop().withKeyValue(
    BootstrapActions.ConfigFile.Hdfs, "dfs.permissions", "false"
  ).build()

  val installSpark = new BootstrapActionConfig("Install Spark",
    new ScriptBootstrapActionConfig(installSparkBootstrap, List[String]())
  )

  val stepFactory = new StepFactory

  val enableDebugging = new StepConfig()
    .withName("Enable Debugging")
    .withActionOnFailure("TERMINATE_JOB_FLOW")
    .withHadoopJarStep(stepFactory.newEnableDebuggingStep())

  val sparkStep = new StepConfig()
    .withName("TweetWordCount")
    .withActionOnFailure("CANCEL_AND_WAIT")
    .withHadoopJarStep(
      stepFactory.newScriptRunnerStep(
        runSpark, assembly,
        "org.boringtechiestuff.spark.TweetWordCount",
        input,
        output
      )
    )

  val job = new RunJobFlowRequest()
    .withName(name)
    .withLogUri(logUri)
    .withInstances(instances)
    .withBootstrapActions(relaxHdfsPermissions, installSpark)
    .withSteps(enableDebugging, sparkStep)

  emr.runJobFlow(job)
}
