package pt.ulisboa.tecnico.cnv.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


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

public class DynamoHandler {

    static AmazonDynamoDB dynamoDB;
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
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("eu-west-3")
                .build();
    }

    public static void newMetrics(int icount, int load_count, int store_count, int new_count, int new_array_count,
                                   int height, int width, int area, String scan_type, String map_image){
        //SAVES METRICS
        String metric_id = UUID.randomUUID().toString().replace("-", "");

        Map<String, AttributeValue> item = newMetricItem(metric_id,icount,load_count,store_count,new_count,new_array_count);
        PutItemRequest putItemRequest = new PutItemRequest(METRICS_TABLE, item);
        PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);

        //SAVES REQUEST ARGUMENTS
        item = newRequestItem(UUID.randomUUID().toString().replace("-", ""),metric_id,height,width,area,scan_type,map_image);
        putItemRequest = new PutItemRequest(REQUESTS_TABLE, item);
        putItemResult = dynamoDB.putItem(putItemRequest);

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