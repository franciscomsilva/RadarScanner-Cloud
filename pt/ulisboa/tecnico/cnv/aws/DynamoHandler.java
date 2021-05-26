package pt.ulisboa.tecnico.cnv.aws;

import java.util.*;


import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.*;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.*;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;

import pt.ulisboa.tecnico.cnv.models.*;

public class DynamoHandler {

    static AmazonDynamoDB dynamoDBClient;
    static DynamoDB dynamoDB;
    private static final String REQUESTS_TABLE = "cnv-proj-requests";
    private static final String METRICS_TABLE = "cnv-proj-metrics";

    public static void init() throws Exception {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("eu-west-3")
                .build();

        dynamoDB = new DynamoDB(dynamoDBClient);
    }

    public static HashMap<String, Request>  getRequests(){

        Table requests_table = dynamoDB.getTable(REQUESTS_TABLE);
        HashMap<String, Request> newRequests = new HashMap<>();

        ScanSpec scanSpec = new ScanSpec().withProjectionExpression("id,area, map_image, metrics_id,scan_type,width,height");

        try {
            ItemCollection<ScanOutcome> items = requests_table.scan(scanSpec);

            Iterator<Item> iter = items.iterator();
            while (iter.hasNext()) {
                Item item = iter.next();
                Request request = new Request(item.getString("id"),item.getString("metrics_id"), item.getInt("area"), item.getInt("height"), item.getInt("width"), item.getString("map_image"), item.getString("scan_type"));
                newRequests.put(request.getId(),request);
            }

        }
        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }


        return newRequests;
    }

    public static HashMap<String,Metric> getMetrics(){

        Table metrics_table = dynamoDB.getTable(METRICS_TABLE);
        HashMap<String, Metric> metrics = new HashMap<>();

        ScanSpec scanSpec = new ScanSpec().withProjectionExpression("id,i_count,load_count, new_array, new_count,store_count");

        try {
            ItemCollection<ScanOutcome> items = metrics_table.scan(scanSpec);

            Iterator<Item> iter = items.iterator();
            while (iter.hasNext()) {
                Item item = iter.next();
                Metric metric = new Metric(item.getString("id"),item.getInt("i_count"), item.getInt("load_count"), item.getInt("new_array"),item.getInt("new_count"),item.getInt("store_count"));
                metrics.put(metric.getId(),metric);
            }

        }
        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }


        return metrics;
    }

    public static HashMap<String,Metric> getMetric(String metrics_id){

        Table metrics_table = dynamoDB.getTable(METRICS_TABLE);
        HashMap<String,Metric> metrics = new HashMap<>();

        HashMap<String, Object> valueMap = new HashMap<String, Object>();
        valueMap.put(":metric_id", metrics_id);

        QuerySpec querySpec = new QuerySpec().withKeyConditionExpression("id = :metric_id")
                .withValueMap(valueMap);

        ItemCollection<QueryOutcome> items = null;
        Iterator<Item> iterator = null;
        Item item = null;

        try {
            items = metrics_table.query(querySpec);

            iterator = items.iterator();
            while (iterator.hasNext()) {
                item = iterator.next();
                Metric metric = new Metric(item.getString("id"), item.getInt("i_count"), item.getInt("load_count"), item.getInt("new_array"), item.getInt("new_count"), item.getInt("store_count"));
                metrics.put(metric.getId(), metric);

            }
        }
        catch (Exception e) {
            System.err.println("Unable to query movies from 1985");
            System.err.println(e.getMessage());
        }

        if(metrics.size() > 1){
            System.err.println("Error: Wrong metric ID");
            return null;
        }

        return metrics;
    }


    public static void newMetrics(int icount, int load_count, int store_count, int new_count, int new_array_count,
                                   int height, int width, int area, String scan_type, String map_image){
        //SAVES METRICS
        String metric_id = UUID.randomUUID().toString().replace("-", "");

        Map<String, AttributeValue> item = newMetricItem(metric_id,icount,load_count,store_count,new_count,new_array_count);
        PutItemRequest putItemRequest = new PutItemRequest(METRICS_TABLE, item);
        PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);

        //SAVES REQUEST ARGUMENTS
        item = newRequestItem(UUID.randomUUID().toString().replace("-", ""),metric_id,height,width,area,scan_type,map_image);
        putItemRequest = new PutItemRequest(REQUESTS_TABLE, item);
        putItemResult = dynamoDBClient.putItem(putItemRequest);

    }

    private static Map<String, AttributeValue> newMetricItem(String id, int icount, int load_count, int store_count, int new_count, int new_array_count) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("id",  new AttributeValue(id));
        item.put("i_count",  new AttributeValue().withN(Integer.toString(icount)));
        item.put("load_count",  new AttributeValue().withN(Integer.toString(load_count)));
        item.put("new_array",  new AttributeValue().withN(Integer.toString(new_array_count)));
        item.put("new_count",  new AttributeValue().withN(Integer.toString(new_count)));
        item.put("store_count",  new AttributeValue().withN(Integer.toString(store_count)));

        return item;
    }

    private static Map<String, AttributeValue> newRequestItem(String id,String metrics_id,int height, int width, int area, String scan_type, String map_image) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("id",  new AttributeValue(id));
        item.put("height",  new AttributeValue().withN(Integer.toString(height)));
        item.put("width",  new AttributeValue().withN(Integer.toString(width)));
        item.put("area",  new AttributeValue().withN(Integer.toString(area)));
        item.put("scan_type",  new AttributeValue(scan_type));
        item.put("map_image",  new AttributeValue(map_image));
        item.put("metrics_id",  new AttributeValue(metrics_id));

        return item;
    }


}