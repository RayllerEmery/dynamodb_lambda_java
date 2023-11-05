package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String tableName = "tableName";
    private static final String region = "us-east-1";

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        var dynamoDbClient = DynamoDbClient.builder().region(Region.of(region)).build();

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        try {


            GetItemResponse getItemResponse;
            PutItemResponse putItemResponse;

            var objectMapper = new ObjectMapper();

            if (input.getHttpMethod().equals("GET")) {
                var id = input.getPathParameters().get("id");
                getItemResponse = getItem(id, dynamoDbClient);
                if (getItemResponse != null && !getItemResponse.toString().isEmpty()) {
                    System.out.println(getItemResponse.item());
                    Map<String, String> objectToResponse = new HashMap<>();

                    for (Map.Entry<String, AttributeValue> entry : getItemResponse.item().entrySet()) {
                        var key = entry.getKey();
                        var value = entry.getValue().s();

                        objectToResponse.put(key, value);
                    }


                    var json = objectMapper.writeValueAsString(objectToResponse);
                    response.withStatusCode(200)
                            .withBody(json);
                }
            } else {
                LinkedHashMap<String, String> object = objectMapper.readValue(input.getBody(), LinkedHashMap.class);
                Map<String, AttributeValue> objectToSave = new HashMap<>();

                for (Map.Entry<String, String> entry : object.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue();

                    objectToSave.put(key, AttributeValue.builder().s(value).build());
                }
                putItemResponse = (PutItemResponse) putItem(objectToSave, dynamoDbClient);

                if (putItemResponse != null && !putItemResponse.toString().isEmpty()) {
                    response.withStatusCode(201);
                }
            }

            return response;

        } catch (IOException e) {

            return response
                    .withBody("{}")
                    .withStatusCode(500);
        }
    }


    private GetItemResponse getItem(String id, DynamoDbClient dynamoDbClient) {
        Map<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("id", AttributeValue.builder().s(id).build());
        var request = GetItemRequest.builder()
                .key(keyToGet)
                .tableName(tableName)
                .build();
        GetItemResponse itemResponse = dynamoDbClient.getItem(request);

        if (itemResponse != null && !itemResponse.item().isEmpty()) {
            return itemResponse;
        } else {
            return GetItemResponse.builder().build();
        }
    }

    private DynamoDbResponse putItem(Map<String, AttributeValue> item, DynamoDbClient dynamoDbClient) {
        if (item.get("id") == null) {
            return GetItemResponse.builder().item(Map.of("error", AttributeValue.builder().s("id não pode ser nulo").build())).build();
        }
        try {
            var putItemRequest = PutItemRequest.builder().item(item).tableName(tableName).build();
            return dynamoDbClient.putItem(putItemRequest);
        } catch (Exception e) {
            System.out.println("Error on try save object: " + item);
            throw new RuntimeException(e);
        }
    }
}
