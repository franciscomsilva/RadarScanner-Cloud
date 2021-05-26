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
import java.util.ArrayList;
import java.util.concurrent.*;

import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.server.*;
import pt.ulisboa.tecnico.cnv.models.*;


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



public class AutoScalerLB{
    static ServerArgumentParser sap = null;
    static HashMap<String,Request> requests = new HashMap<>();
    static HashMap<String,Metric> metrics = new HashMap<>();



    public static void main(final String[] args) throws Exception{


        try {
            // Get user-provided flags.
            AutoScalerLB.sap = new ServerArgumentParser(args);
        }
        catch(Exception e) {
            System.out.println(e);
            return;
        }


        /*PERIODIC PULL OF DYNAMO DB REQUESTS*/
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try{
                    DynamoHandler.init();
                    requests = DynamoHandler.getRequests();
                    metrics = DynamoHandler.getMetrics();
                }catch(Exception e) {
                    System.err.println(e.getMessage());
                }

            }
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(task, 0L,2, TimeUnit.MINUTES);


        /*CREATES WEB SERVER*/
        final HttpServer server = HttpServer.create(new InetSocketAddress(AutoScalerLB.sap.getServerAddress(), AutoScalerLB.sap.getServerPort()), 0);
        server.createContext("/scan", new LBHandler());

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();


    }

    public static void init()throws Exception{
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        ProfileCredentialsProvider credentialsProvider=new ProfileCredentialsProvider();
        try{
            credentialsProvider.getCredentials();
        }catch(Exception e){
            throw new AmazonClientException(
            "Cannot load the credentials from the credential profiles file. "+
            "Please make sure that your credentials file is at the correct "+
            "location (~/.aws/credentials), and is in valid format.",e);
        }

    }

    static class LBHandler implements HttpHandler {

        @Override
        public void handle(final HttpExchange t) throws IOException {
            /*ANALYZES REQUEST AND IT'S WEIGHT*/

            /*STARTS BY SEPARATING QUERY PARAMETERS*/

            final String query = t.getRequestURI().getQuery();

            System.out.println("> AB Received Query:\t" + query);

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
            int area = (y1 - y0) * (x1-x0);


            /*DETERMINES THE WEIGHT OF THE REQUEST*/

            /*GETS STORED REQUESTS*/

            try{
                DynamoHandler.init();
                requests = DynamoHandler.getRequests();
                System.out.println(requests);
            }catch(Exception e){
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

            int i_count_final= 0;
            int load_count_final = 0;
            int new_array_final = 0;
            int new_count_final = 0;
            int store_count_final = 0;

            int counter_scan=0, counter_map = 0, counter_area=0;


            /*COMPARE RECEIVED REQUESTS WITH STORED REQUESTS*/
            for (Map.Entry<String,Request> entry : requests.entrySet()) {
                if(entry.getValue().getScan_type().equals(scan_type) && entry.getValue().getMap_image().equals(map_image) && entry.getValue().getArea() == area){
                    equalRequest = entry.getValue();
                    flagEqual = true;
                    break;
                }
            }

            /*NO REQUEST EXACTLY EQUAL TO THE ONE RECEIVED*/
            if(!flagEqual){
                for (Map.Entry<String,Request> entry : requests.entrySet()) {
                    if(entry.getValue().getScan_type().equals(scan_type)) {
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_scan += metric.getI_count();
                        load_count_scan += metric.getLoad_count();
                        new_array_scan += metric.getNew_array();
                        new_count_scan += metric.getNew_count();
                        store_count_scan += metric.getStore_count();
                        counter_scan++;
                    }
                    if(entry.getValue().getMap_image().equals(map_image)){
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_map += metric.getI_count();
                        load_count_map += metric.getLoad_count();
                        new_array_map += metric.getNew_array();
                        new_count_map += metric.getNew_count();
                        store_count_map += metric.getStore_count();
                        counter_map++;
                    }

                    if(entry.getValue().getArea() == area ){
                        Metric metric = metrics.get(entry.getValue().getMetrics_id());
                        i_count_area += metric.getI_count();
                        load_count_area += metric.getLoad_count();
                        new_array_area += metric.getNew_array();
                        new_count_area += metric.getNew_count();
                        store_count_area += metric.getStore_count();
                        counter_area++;
                    }

                }
                float scan_weight = 0.5;
                float map_weight = 0.3;
                float area_weight = 0.2;

                float[][] weights_matrix = new float[][]{
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
                if(counter_scan > 0){
                    i_count_scan = i_count_scan/counter_scan;
                    load_count_scan = load_count_scan/counter_scan;
                    new_array_scan = new_array_scan/counter_scan;
                    new_count_scan = new_count_scan/counter_scan;
                    store_count_scan = store_count_scan/counter_scan
                }

                if(counter_map > 0){
                    i_count_map = i_count_map/counter_map;
                    load_count_map = load_count_map/counter_map;
                    new_array_map = new_array_map/counter_map;
                    new_count_map = new_count_map/counter_map;
                    store_count_map = store_count_map/counter_map
                }

                if(counter_area > 0){
                    i_count_area = i_count_area/counter_area;
                    load_count_area = load_count_area/counter_area;
                    new_array_area = new_array_area/counter_area;
                    new_count_area = new_count_area/counter_area;
                    store_count_area = store_count_area/counter_area
                }

                /*CALCULATES THE WEIGHTS*/
                counter_scan > 0 ? matrix_size = 



                /*CALCULATES FINAL VALUES USING A PERCENTAGE OF WEIGHT THE  PARAMETERS - SCAN TYPE IS THE MOST IMPORTANTE, FOLLOWED BY MAP AND BY AREA*/
                i_count_final =(int) scan_weight * i_count_scan + map_weight * i_count_map + area_weight * i_count_area;
                load_count_final =(int) scan_weight * load_count_scan + map_weight * load_count_map + area_weight * load_count_area;
                new_array_final =(int) scan_weight * new_array_scan + map_weight * new_array_map + area_weight * new_array_area;
                new_count_final = (int) scan_weight * new_count_scan + map_weight * new_count_map + area_weight * new_count_are;
                store_count_final = (int) scan_weight * store_count_scan + map_weight * store_count_map + area_weight * store_count_area;

            }
            /*GETS THE METRICS FOR THE SIMILAR STORED REQUEST*/
            else{
                Metric final_metric = metrics.get(equalRequest.getMetrics_id());
                i_count_final = final_metric.getI_count();
                load_count_final = final_metric.getLoad_count();
                new_array_final = final_metric.getNew_array();
                new_count_final = final_metric.getNew_count();
                store_count_final = final_metric.getStore_count();
            }

            /*CALCULATE WEIGHT BASED ON METRICS*/
            


            /*ANALYZES CURRENT LOAD OF ALL INSTANCES*/



            /*CHOOSES INSTANCE WITH LEAST LOAD*/


            /*ROUTE REQUEST TO THAT INSTANCE*/

        }

    }

    static class ASHandler implements HttpHandler {

        @Override
        public void handle(final HttpExchange t) throws IOException {


        }

    }


}