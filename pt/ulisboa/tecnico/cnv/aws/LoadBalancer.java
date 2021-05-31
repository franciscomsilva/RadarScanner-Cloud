package pt.ulisboa.tecnico.cnv.aws;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.server.*;
import pt.ulisboa.tecnico.cnv.models.*;

import java.nio.file.Files;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;



import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
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
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;




public class LoadBalancer {
    private static ServerArgumentParser sap = null;
    public static HashMap<String, Request> requests = new HashMap<>();
    private static HashMap<String, Metric> metrics = new HashMap<>();
    public static HashMap<String, Double> instance_load = new HashMap<>();
    public static HashMap<String, Instance> instance_by_id = new HashMap<>();
    private static AmazonEC2 ec2;
    private static AmazonCloudWatch cloudWatch;
    private static AtomicInteger request_counter = new AtomicInteger(0);


    private static final int INSTANCE_PORT = 8000;


    public static void execute(final String[] args) throws Exception {


        try {
            // Get user-provided flags.
            LoadBalancer.sap = new ServerArgumentParser(args);
        } catch (Exception e) {
            System.err.println(e);
            return;
        }


        /*PERIODIC PULL OF DYNAMO DB REQUESTS*/
        Runnable taskPull = new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("LB -> Getting metrics from MSS and instances from AWS\n");
                    getInstances();
                    DynamoHandler.init();
                    requests = DynamoHandler.getRequests();
                    metrics = DynamoHandler.getMetrics();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

            }
        };

        /*PERIODIC HEALTH CHECK OF INSTANCES*/
        Runnable taskHealth = new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("LB -> Health Check of Instances\n");
                    getInstances();
                    for (Map.Entry<String, Instance> entry : instance_by_id.entrySet()) {
                        if(!healthCheck(entry.getKey())){
                            if(entry.getValue().getState().getName().equals("pending"))
                                continue;

                            /*WAITS FOR POSSIBLE BOOT*/
                            Thread.sleep(25000);
                            if(!healthCheck(entry.getKey())){
                                System.out.println("LB -> Health Check failed on instance " + entry.getKey() + "\n");
                                AutoScaler.terminateInstance(entry.getKey());
                                AutoScaler.createInstances(1);
                                Thread.sleep(60000);
                                getInstances();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

            }
        };


        ScheduledExecutorService schedulerPull = Executors.newScheduledThreadPool(1);
        schedulerPull.scheduleAtFixedRate(taskPull, 0L, 1, TimeUnit.MINUTES);

        ScheduledExecutorService schedulerHealth = Executors.newScheduledThreadPool(1);
        schedulerHealth.scheduleAtFixedRate(taskHealth, 0L, 2, TimeUnit.MINUTES);

        /*ALLOW TO GET INSTANCES*/
        Thread.sleep(3000);

        /*CREATES WEB SERVER*/
        final HttpServer server = HttpServer.create(new InetSocketAddress(LoadBalancer.sap.getServerAddress(), 80), 0);
        server.createContext("/scan", new LBHandler());

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("LB -> Started \n");


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

    static class LBHandler implements HttpHandler {

        @Override
        public void handle(final HttpExchange t) throws IOException {
            /*ANALYZES REQUEST AND IT'S WEIGHT*/

            /*STARTS BY SEPARATING QUERY PARAMETERS*/

            final String query = t.getRequestURI().getQuery();
            int local_request_counter = request_counter.incrementAndGet();
            System.out.println("LB -> Received query from request " + local_request_counter + " : " + query + "\n");


            // Break it down into String[].
            final String[] params = query.split("&");

            /*RETRIEVES PARAMETER VALUES*/


            int y1 = Integer.parseInt(params[5].split("y1=")[1]);
            int y0 = Integer.parseInt(params[4].split("y0=")[1]);
            int x1 = Integer.parseInt(params[3].split("x1=")[1]);
            int x0 = Integer.parseInt(params[2].split("x0=")[1]);

            int height = Integer.parseInt(params[1].split("h=")[1]);
            int width = Integer.parseInt(params[0].split("w=")[1]);

            String scan_type = params[8].split("s=")[1];
            String map_image = params[9].split("i=")[1];
            int area = (y1 - y0) * (x1 - x0);


            /*DETERMINES THE WEIGHT OF THE REQUEST*/
            /*GETS STORED REQUESTS*/

            try {
                DynamoHandler.init();
                requests = DynamoHandler.getRequests();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return;
            }

            Request equalRequest = null;
            Metric finalMetrics = null;
            boolean flagEqual = false;

            long i_count_scan = 0;
            long load_count_scan = 0;
            long store_count_scan = 0;

            long i_count_map = 0;
            long load_count_map = 0;
            long store_count_map = 0;

            long i_count_area = 0;
            long load_count_area = 0;
            long store_count_area = 0;

            long i_count_final = 0;
            long load_count_final = 0;
            long store_count_final = 0;

            int counter_scan = 0, counter_map = 0, counter_area = 0;


            /*COMPARE RECEIVED REQUESTS WITH STORED REQUESTS*/
            for (Map.Entry<String, Request> entry : requests.entrySet()) {
                if (entry.getValue().getScan_type().equals(scan_type) && entry.getValue().getMap_image().equals(map_image) && entry.getValue().getArea() == area) {
                    equalRequest = entry.getValue();
                    flagEqual = true;
                    break;
                }
            }


            /*NO REQUEST EXACTLY EQUAL TO THE ONE RECEIVED*/
            if (!flagEqual) {
                for (Map.Entry<String, Request> entry : requests.entrySet()) {
                    if (entry.getValue().getScan_type().equals(scan_type)) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_scan += metric.getI_count();
                        load_count_scan += metric.getLoad_count();
                        store_count_scan += metric.getStore_count();
                        counter_scan++;
                    }
                    if (entry.getValue().getMap_image().equals(map_image)) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_map += metric.getI_count();
                        load_count_map += metric.getLoad_count();
                        store_count_map += metric.getStore_count();
                        counter_map++;
                    }

                    if (entry.getValue().getArea() == area) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_area += metric.getI_count();
                        load_count_area += metric.getLoad_count();
                        store_count_area += metric.getStore_count();
                        counter_area++;
                    }

                }

                double scan_weight = 0.5;
                double map_weight = 0.3;
                double area_weight = 0.2;

                Integer[] weights_position = {1, 1, 1, 1, 1, 1, 1, 1};

                double[][] weights_matrix = new double[][]{
                        {0.5, 0.3, 0.3},
                        {0.6, 0.4, 0},
                        {0.65, 0, 0.35},
                        {1, 0, 0},
                        {0, 0.55, 0.45},
                        {0, 1, 0},
                        {0, 0, 1},
                        {0, 0, 0}
                };

                /*GETS THE AVERAGE FOR EACH REQUEST PARAMETER AND ADJUSTS THE PERCENTAGE IF NO REQUESTS ARE FOUND */
                if (counter_scan > 0) {
                    i_count_scan = i_count_scan / counter_scan;
                    load_count_scan = load_count_scan / counter_scan;
                    store_count_scan = store_count_scan / counter_scan;

                    weights_position[7] = weights_position[6] = weights_position[5] = weights_position[4] = 0;
                } else {
                    weights_position[0] = weights_position[1] = weights_position[2] = weights_position[3] = 0;
                }

                if (counter_map > 0) {
                    i_count_map = i_count_map / counter_map;
                    load_count_map = load_count_map / counter_map;
                    store_count_map = store_count_map / counter_map;

                    weights_position[2] = weights_position[3] = weights_position[6] = weights_position[7] = 0;
                } else {
                    weights_position[0] = weights_position[1] = weights_position[4] = weights_position[5] = 0;
                }

                if (counter_area > 0) {
                    i_count_area = i_count_area / counter_area;
                    load_count_area = load_count_area / counter_area;
                    store_count_area = store_count_area / counter_area;

                    weights_position[1] = weights_position[3] = weights_position[5] = weights_position[7] = 0;
                } else {
                    weights_position[0] = weights_position[2] = weights_position[4] = weights_position[6] = 0;
                }
                int position = Arrays.asList(weights_position).indexOf(1);
                scan_weight = weights_matrix[position][0];
                map_weight = weights_matrix[position][1];
                area_weight = weights_matrix[position][2];


                /*CALCULATES FINAL VALUES USING A PERCENTAGE OF WEIGHT THE  PARAMETERS - SCAN TYPE IS THE MOST IMPORTANTE, FOLLOWED BY MAP AND BY AREA*/
                i_count_final = (long) scan_weight * i_count_scan + (long) map_weight * i_count_map + (long) area_weight * i_count_area;
                load_count_final = (long) scan_weight * load_count_scan + (long) map_weight * load_count_map + (long) area_weight * load_count_area;
                store_count_final = (long) scan_weight * store_count_scan + (long) map_weight * store_count_map + (long) area_weight * store_count_area;


            }
            /*GETS THE METRICS FOR THE SIMILAR STORED REQUEST*/
            else {
                Metric final_metric = metrics.get(equalRequest.getMetrics_id());
                i_count_final = final_metric.getI_count();
                load_count_final = final_metric.getLoad_count();
                store_count_final = final_metric.getStore_count();

            }

            /*CALCULATE WEIGHT BASED ON METRICS*/
            double final_request_weight = ((double) i_count_final * 0.5 + (double) load_count_final * 0.25 + (double) store_count_final * 0.25);

            if(final_request_weight == 0)
                final_request_weight = 323059012 * 2;

            getInstances();
            Instance instance = null;
            boolean flagExit = false;
            int exitCounter = 0;
            Map.Entry<String, Double> first_entry = instance_load.entrySet().iterator().next();
            double min_load = first_entry.getValue();
            String instance_id = first_entry.getKey();
            HashMap<String, Double> local_instance_load = instance_load;
            HashMap<String, Instance> local_instance_by_id = instance_by_id;

            while (!flagExit && exitCounter < 10){
                /*ANALYZES CURRENT LOAD OF ALL INSTANCES AND CHOOSES INSTANCE WITH LEAST LOAD*/
                for (Map.Entry<String, Double> entry : local_instance_load.entrySet()) {
                    if (entry.getValue() < min_load) {
                        min_load = entry.getValue();
                        instance_id = entry.getKey();
                    }
                }
                /*GET INSTANCES*/
                instance = local_instance_by_id.get(instance_id);

                try{
                    if(healthCheck(instance_id)) {
                        flagExit = true;
                        System.out.println("LB -> Redirecting request " + local_request_counter + " to instance " + instance_id + "\n");
                    }else{
                        /*IF ONLY INSTANCE AVAILABLE REMOVE AND CREATE ANOTHER ONE*/
                        System.err.println("LB -> ERROR: Instance " + instance.getInstanceId() + " not responsive. Resetting!\n");
                        if(local_instance_by_id.size() <= 1){
                            AutoScaler.terminateInstance(instance_id);
                            instance_by_id.remove(instance_id);
                            instance_load.remove(instance_id);
                            AutoScaler.createInstances(1);
                            Thread.sleep(60000);
                            getInstances();
                            local_instance_by_id = instance_by_id;
                            local_instance_load = instance_load;
                            first_entry = local_instance_load.entrySet().iterator().next();
                            min_load = first_entry.getValue();
                            instance_id = first_entry.getKey();
                        /*IF OTHER INSTANCES EXIST, REDIRECT TO ANOTHER AND ALLOW PERIODIC HEALTH CHECK TO REMOVE IF IT STILL DOESNT RESPOND*/
                        }else{
                            /*REMOVES FROM THE LOCAL LIST TO CHOOSE ANOTHER AND REBOOT*/
                            local_instance_load.remove(instance_id);
                            local_instance_by_id.remove(instance_id);

                            RebootInstancesRequest request = new RebootInstancesRequest()
                                    .withInstanceIds(instance_id);

                            RebootInstancesResult response = ec2.rebootInstances(request);

                        }
                    }
                }catch(Exception e ){
                    System.err.println("LB-> ERROR: " + e.getMessage() + "\n");
                    e.printStackTrace();
                }

                exitCounter++;
            }


            /*ROUTE REQUEST TO THAT INSTANCE*/
            /*ADDS LOAD*/
            double current_instance_load = instance_load.get(instance_id);
            current_instance_load += final_request_weight;
            instance_load.put(instance_id, current_instance_load);

            /*ROUTES REQUEST TO INSTANCE*/
            final Headers hdrs = t.getResponseHeaders();
            hdrs.add("Content-Type", "image/png");
            hdrs.add("Access-Control-Allow-Origin", "*");
            hdrs.add("Access-Control-Allow-Credentials", "true");
            hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
            hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");


            HttpURLConnection connection = null;
            try{
                connection = sendRequestToInstance(instance_id, query);
            }catch(Exception e){
                System.err.println("ERROR: " + e.getMessage());
                return;
            }
            //connection.setConnectTimeout(5000);
            //connection.setReadTimeout(5000);


            InputStream in = connection.getInputStream();
            OutputStream os = t.getResponseBody();

            t.sendResponseHeaders(200, connection.getContentLength());
            /*SENDS RESPONSE BACK TO CLIENT*/
            try {
                byte[] buf = new byte[8192];
                int length;
                while ((length = in.read(buf)) > 0) {
                    os.write(buf, 0, length);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            os.close();
            in.close();
            connection.disconnect();

            System.out.println("LB -> Sent response from request " + local_request_counter + " back to " + t.getRemoteAddress().toString() + "\n");

            /*REMOVES LOAD*/
            current_instance_load = instance_load.get(instance_id);
            current_instance_load -= final_request_weight;
            instance_load.put(instance_id, current_instance_load);


        }

    }

    private static HttpURLConnection sendRequestToInstance(String instance_id, String query) throws IOException {
        Instance instance = instance_by_id.get(instance_id);

        URL url = new URL("http://" + instance.getPublicIpAddress() + ":" + INSTANCE_PORT + "/scan?" + query);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        return connection;
    }

    public static boolean healthCheck(String instance_id) throws Exception{
        /*GETS UPDATED INSTANCE IN CASE IP WAS NOT AVAILABLE YET*/
        Instance instance = getInstance(instance_id);

        /*CHECKS 3 TIMES*/
        URL url = new URL("http://" + instance.getPublicIpAddress() + ":" + INSTANCE_PORT + "/test");
        int status = 0, counter = 0;
        while(counter < 3){
            try{
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                status = connection.getResponseCode();
                connection.disconnect();
                if(status == 200)
                    return true;

                Thread.sleep(500);
            }catch(Exception e){

            }
            counter++;
        }

        return false;
    }

    public static Instance getInstance(String instance_id){
        ArrayList<String> instances_ids = new ArrayList<>();
        instances_ids.add(instance_id);
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(instances_ids);
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances(request);
        List<Reservation> reservations = describeInstancesResult.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            if(instances.size() == 1)
                return instances.get(0);
        }
        return null;
    }

    public static void getInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        double load = 0.0;
        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                load = 0.0;
                if(instance.getState().getName().equals("running") || instance.getState().getName().equals("pending")){
                    String instance_id = instance.getInstanceId();

                    /*IF INSTANCE ALREADY EXISTS COPYS LOAD AND UPDATE, SOME DATA MIGHT NOT BE UPDATED*/
                    if(instance_load.containsKey(instance_id) && instance_by_id.containsKey(instance_id)){
                        load = instance_load.get(instance_id);
                    }
                    instance_load.put(instance_id, load);
                    instance_by_id.put(instance_id, instance);
                }

            }
        }
    }



}