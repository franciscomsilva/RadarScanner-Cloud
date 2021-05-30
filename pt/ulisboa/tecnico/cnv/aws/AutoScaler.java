package pt.ulisboa.tecnico.cnv.aws;

import java.io.*;
import java.lang.Thread;
import java.util.HashMap;
import java.util.Map;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

public class AutoScaler {
    private static AmazonEC2 ec2;
    private static AmazonCloudWatch cloudWatch;
    private static final double CPU_UPPER_LOAD = 70;
    private static final double CPU_LOWER_LOAD = 30;
    /*THIS ROUGHLY CORRESPONDS TO TWO LIGHT REQUESTS PER INSTANCE*/
    private static final double METRIC_UPPER_LOAD = 323059012 * 2;
    private static final String AMI_IMAGE_ID ="ami-0423040cddd106f1e";
    private static final String SSH_KEY_NAME = "CNV-proj-key";
    private static final String SECURITY_GROUP_NAME ="CNV-proj-ssh+http";
    private static final String INSTANCE_TYPE_NAME = "t2.micro";


    public static void execute() throws InterruptedException{
        HashSet<Instance> instances = null;
        System.out.println("AS -> Started\n");


        while(true){
            /*VERIFY IF ANY INSTANCES ARE RUNNING*/
            instances = getInstances();
            /*IF NOT, CREATE ONE*/
            if(instances.size() <= 0){
                System.out.println("AS -> Adding one instance\n");
                createInstances(1);
                Thread.sleep(3000);
                instances = getInstances();
                /*SLEEPS FOR 10 SECONDS*/
                Thread.sleep(10000);
                continue;
            }


            /*IF THERE IS, MONITOR THEM WITH CPU USAGE AND METRICS*/
            double global_cpu_average = 0;
            for(Instance instance : instances){
                global_cpu_average += getInstanceCPUAverage(instance);
            }
            global_cpu_average = global_cpu_average / instances.size();
            System.out.println("AS -> Average System CPU Utilization " + String.format("%.2f", global_cpu_average) + "%\n");

            /* IF THE LOAD IS ABOVE 70% WE CHECK THE SAVED METRICS ON THE LB*/
            if(global_cpu_average > CPU_UPPER_LOAD){
                int global_metric_load = 0, counter = 0;
                for (Map.Entry<String, Double> entry : LoadBalancer.instance_load.entrySet()) {
                    global_metric_load += entry.getValue();
                    counter++;
                }

                global_metric_load = global_metric_load / counter;

                /*IF ABOVE ADDS INSTANCE OTHERWISE DOES NOTHING*/
                if(global_metric_load > METRIC_UPPER_LOAD){
                    /*ADDS INSTANCE*/
                    System.out.println("AS -> Adding one instance \n");
                    createInstances(1);
                }

            }
            /* REMOVES ONE INSTANCE THAT HAS NO REQUEST PENDENT (LOAD == 0) | LOOPS MAX THREE TIMES IN CASE THERE ARE NO INSTANCEs WITH LOAD 0 AT THE TIME*/
            else if (global_cpu_average < CPU_LOWER_LOAD){
                if(instances.size() > 1){
                    int counter = 0;
                    String instance_id;
                    boolean exit_flag = false;
                    while(!exit_flag && counter < 3){
                        for(Instance instance : instances){
                            /*GETS THAT INSTANCE LOAD AND CHECK IF ZERO, AND IF SO REMOVES*/
                            if(LoadBalancer.instance_load.get(instance.getInstanceId()) <= 0){
                                terminateInstance(instance.getInstanceId());
                                System.out.println("AS -> Terminating one instance \n");
                                exit_flag = true;
                                break;
                            }

                        }
                        counter++;
                        Thread.sleep(2000);
                    }
                }
            }


            /*SLEEPS FOR 10 SECONDS*/
            Thread.sleep(10000);
        }

    }

    public static void init() throws Exception {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        AWSCredentials credentials = null;
        try {
            credentials = credentialsProvider.getCredentials();
            ec2 = AmazonEC2ClientBuilder.standard().withRegion("eu-west-3").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
            cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-3").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.", e);
        }

    }

    private static void terminateInstance(String instance_id){
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instance_id);
        ec2.terminateInstances(termInstanceReq);
        LoadBalancer.getInstances();
    }

    private static void createInstances (int number_instances){
        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        //TODO CHANGE AMI, KEYNAME AND SECURITY GROUP
        runInstancesRequest.withImageId(AMI_IMAGE_ID)
                .withInstanceType(INSTANCE_TYPE_NAME)
                .withMinCount(1)
                .withMaxCount(number_instances)
                .withKeyName(SSH_KEY_NAME)
                .withSecurityGroups(SECURITY_GROUP_NAME);

        RunInstancesResult runInstancesResult =
                ec2.runInstances(runInstancesRequest);

        LoadBalancer.getInstances();
    }


    private static HashSet<Instance> getInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        HashSet<Instance> instances = new HashSet<>();

        for (Reservation reservation : reservations) {
            for(Instance instance : reservation.getInstances()){
                if(instance.getState().getName().equals("running") || instance.getState().getName().equals("pending"))
                    instances.add(instance);
            }
        }
        return instances;
    }

    private static double getInstanceCPUAverage(Instance instance) {
        String name = instance.getInstanceId();
        String state = instance.getState().getName();
        double final_average = 0;
        int counter = 0;
        long offsetInMilliseconds = 1000 * 60 * 3;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");


        if (state.equals("running")) {
            instanceDimension.setValue(name);
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                    /*UTC TO UTC-1*/
                    .withStartTime(new Date((new Date().getTime() - 1000 * 60 * 60) - offsetInMilliseconds))
                    .withNamespace("AWS/EC2")
                    .withPeriod(30)
                    .withMetricName("CPUUtilization")
                    .withStatistics("Average")
                    .withDimensions(instanceDimension)
                    .withEndTime(new Date());
            GetMetricStatisticsResult getMetricStatisticsResult =
                    cloudWatch.getMetricStatistics(request);
            List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
            for (Datapoint dp : datapoints) {
                System.out.println(dp.getAverage());
                final_average += dp.getAverage();
                counter++;
            };

            if(counter == 0)
                counter = 1;

            final_average = (double) final_average /counter;

        }

        return final_average;
    }

}
