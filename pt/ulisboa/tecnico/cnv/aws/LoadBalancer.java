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



public class LoadBalancer {
    private static ServerArgumentParser sap = null;
    public static HashMap<String, Request> requests = new HashMap<>();
    private static HashMap<String, Metric> metrics = new HashMap<>();
    public static HashMap<String, Integer> instance_load = new HashMap<>();
    public static HashMap<String, Instance> instance_by_id = new HashMap<>();
    private static AmazonEC2 ec2;
    private static AmazonCloudWatch cloudWatch;


    private static final int INSTANCE_PORT = 8000;


    public static void execute(final String[] args) throws Exception {


        try {
            // Get user-provided flags.
            LoadBalancer.sap = new ServerArgumentParser(args);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }


        /*PERIODIC PULL OF DYNAMO DB REQUESTS*/
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    DynamoHandler.init();
                    requests = DynamoHandler.getRequests();
                    metrics = DynamoHandler.getMetrics();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }

            }
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(task, 0L, 2, TimeUnit.MINUTES);


        /*CREATES WEB SERVER*/
        final HttpServer server = HttpServer.create(new InetSocketAddress(LoadBalancer.sap.getServerAddress(), LoadBalancer.sap.getServerPort()), 0);
        server.createContext("/scan", new LBHandler());

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();


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

            System.out.println("LB -> Received query: " + query);


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
                System.out.println(requests);
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return;
            }

            Request equalRequest = null;
            Metric finalMetrics = null;
            boolean flagEqual = false;

            int i_count_scan = 0;
            int load_count_scan = 0;
            int new_array_scan = 0;
            int new_count_scan = 0;
            int store_count_scan = 0;

            int i_count_map = 0;
            int load_count_map = 0;
            int new_array_map = 0;
            int new_count_map = 0;
            int store_count_map = 0;

            int i_count_area = 0;
            int load_count_area = 0;
            int new_array_area = 0;
            int new_count_area = 0;
            int store_count_area = 0;

            int i_count_final = 0;
            int load_count_final = 0;
            int new_array_final = 0;
            int new_count_final = 0;
            int store_count_final = 0;

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
                        new_array_scan += metric.getNew_array();
                        new_count_scan += metric.getNew_count();
                        store_count_scan += metric.getStore_count();
                        counter_scan++;
                    }
                    if (entry.getValue().getMap_image().equals(map_image)) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_map += metric.getI_count();
                        load_count_map += metric.getLoad_count();
                        new_array_map += metric.getNew_array();
                        new_count_map += metric.getNew_count();
                        store_count_map += metric.getStore_count();
                        counter_map++;
                    }

                    if (entry.getValue().getArea() == area) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_area += metric.getI_count();
                        load_count_area += metric.getLoad_count();
                        new_array_area += metric.getNew_array();
                        new_count_area += metric.getNew_count();
                        store_count_area += metric.getStore_count();
                        counter_area++;
                    }

                }
                double scan_weight = 0.5;
                double map_weight = 0.3;
                double area_weight = 0.2;

                int[] weights_position = {1, 1, 1, 1, 1, 1, 1, 1};

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
                    new_array_scan = new_array_scan / counter_scan;
                    new_count_scan = new_count_scan / counter_scan;
                    store_count_scan = store_count_scan / counter_scan;

                    weights_position[7] = weights_position[6] = weights_position[5] = weights_position[4] = 0;
                } else {
                    weights_position[0] = weights_position[1] = weights_position[2] = weights_position[3] = 0;
                }

                if (counter_map > 0) {
                    i_count_map = i_count_map / counter_map;
                    load_count_map = load_count_map / counter_map;
                    new_array_map = new_array_map / counter_map;
                    new_count_map = new_count_map / counter_map;
                    store_count_map = store_count_map / counter_map;

                    weights_position[2] = weights_position[3] = weights_position[6] = weights_position[7] = 0;
                } else {
                    weights_position[0] = weights_position[1] = weights_position[4] = weights_position[5] = 0;
                }

                if (counter_area > 0) {
                    i_count_area = i_count_area / counter_area;
                    load_count_area = load_count_area / counter_area;
                    new_array_area = new_array_area / counter_area;
                    new_count_area = new_count_area / counter_area;
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
                i_count_final = (int) scan_weight * i_count_scan + (int) map_weight * i_count_map + (int) area_weight * i_count_area;
                load_count_final = (int) scan_weight * load_count_scan + (int) map_weight * load_count_map + (int) area_weight * load_count_area;
                new_array_final = (int) scan_weight * new_array_scan + (int) map_weight * new_array_map + (int) area_weight * new_array_area;
                new_count_final = (int) scan_weight * new_count_scan + (int) map_weight * new_count_map + (int) area_weight * new_count_area;
                store_count_final = (int) scan_weight * store_count_scan + (int) map_weight * store_count_map + (int) area_weight * store_count_area;

            }
            /*GETS THE METRICS FOR THE SIMILAR STORED REQUEST*/
            else {
                Metric final_metric = metrics.get(equalRequest.getMetrics_id());
                i_count_final = final_metric.getI_count();
                load_count_final = final_metric.getLoad_count();
                new_array_final = final_metric.getNew_array();
                new_count_final = final_metric.getNew_count();
                store_count_final = final_metric.getStore_count();
            }

            /*CALCULATE WEIGHT BASED ON METRICS*/
            int final_request_weight = (int) ((double) i_count_final * 0.4 + (double) load_count_final * 0.2 + (double) store_count_final * 0.2 + (double) new_array_final * 0.1 + (double) new_count_final * 0.1);

            getInstances();

            /*ANALYZES CURRENT LOAD OF ALL INSTANCES AND CHOOSES INSTANCE WITH LEAST LOAD*/
            int min_load = 0;
            String instance_id = null;
            for (Map.Entry<String, Integer> entry : instance_load.entrySet()) {
                if (entry.getValue() < min_load) {
                    min_load = entry.getValue();
                    instance_id = entry.getKey();
                }
            }

            /*GET INSTANCES*/
            Instance instance = instance_by_id.get(instance_id);
            System.out.println("LB -> Redirecting request to instance " + instance_id);

            /*ROUTE REQUEST TO THAT INSTANCE*/
            /*ADDS LOAD*/
            int current_instance_load = instance_load.get(instance_id);
            current_instance_load += final_request_weight;
            instance_load.put(instance_id, current_instance_load);
            String instance_ip = instance.getPublicIpAddress();

            /*ROUTES REQUEST TO INSTANCE*/
            final Headers hdrs = t.getResponseHeaders();
            hdrs.add("Content-Type", "image/png");
            hdrs.add("Access-Control-Allow-Origin", "*");
            hdrs.add("Access-Control-Allow-Credentials", "true");
            hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
            hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

            HttpURLConnection connection = sendRequestToInstance(instance_ip, query);

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

            System.out.println("LB -> Sent response back to " + t.getRemoteAddress().toString());

            /*REMOVES LOAD*/
            current_instance_load = instance_load.get(instance_id);
            current_instance_load -= final_request_weight;
            instance_load.put(instance_id, current_instance_load);


        }

    }

    private static HttpURLConnection sendRequestToInstance(String instanceIP, String query) throws IOException {

        URL url = new URL("http://" + instanceIP + ":" + INSTANCE_PORT + "/scan?" + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        int status = connection.getResponseCode();

        return connection;
    }


    private static void getInstances() {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                String instance_id = instance.getInstanceId();
                if(instance_load.containsKey(instance_id) && instance_by_id.containsKey(instance_id))
                    continue;
                instance_load.put(instance_id, 0);
                instance_by_id.put(instance_id, instance);
            }
        }
    }



}