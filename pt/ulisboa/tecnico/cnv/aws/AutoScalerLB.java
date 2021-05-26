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
    static ArrayList<Request> requests = new ArrayList<>();


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

            /*COMPARE RECEIVED REQUESTS WITH STORED REQUESTS*/
            for(Request request : requests){
                if()
            }



            /*GETS THE METRICS FOR THE SIMILAR STORED REQUEST*/





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