package edu.njit.pg262;

import java.util.*;
import java.io.*;
import java.nio.file.*;

import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.core.ResponseInputStream;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.TextDetection;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.paginators.ListQueuesIterable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.model.Message;
import com.opencsv.CSVWriter;

public class Instance {

    private S3Client s3;
    private Region region;
    private String bucketName;
    private String queueName;
    private String queueUrl;
    private SqsClient sqs;
    private RekognitionClient rek;
    final private String endOfStream = "-1";
    private Map<String, List<String>> output = new HashMap<>();
    final private String outputLoc = "/home/ec2-user/pa1/output/";
    final private String msgGroup = "pa1";

    public Instance(Region region, String bucketName, String queueName) {
        this.region = region;
        this.bucketName = bucketName;
        this.queueName = queueName;
    }

    private enum ClientType {
        S3,
        REKOGNITION,
        SQS
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public <T> T connect(ClientType clientType) {
        switch (clientType) {
            case S3:
                return (T) S3Client.builder().region(this.region).build();
            case REKOGNITION:
                return (T) RekognitionClient.builder().region(this.region).build();
            case SQS:
                return (T) SqsClient.builder().region(this.region).build();
            default:
                throw new IllegalArgumentException("Unsupported Client Type: " + clientType);
        }
    }

    public void connect() {
        this.s3 = connect(ClientType.S3);
        this.sqs = connect(ClientType.SQS);
        this.queueUrl = getQueueUrl();
        this.rek = connect(ClientType.REKOGNITION);
    }

    public void disconnect() {
        this.s3.close();
        this.sqs.close();
        this.rek.close();
    }

    private List<S3Object> listBucketObjects() {
        try {
            ListObjectsRequest listObjects = ListObjectsRequest.builder()
                    .bucket(this.bucketName)
                    .build();
        
            ListObjectsResponse response = this.s3.listObjects(listObjects);
            
            return response.contents();

        } catch (S3Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        return null;

    }

    private GetObjectRequest createObjectRequest(S3Object obj) {
        return GetObjectRequest.builder()
                        .bucket(this.bucketName)
                        .key(obj.key())
                        .build();
    }

    private GetObjectRequest createObjectRequest(String keyName) {
        return GetObjectRequest.builder()
                        .bucket(this.bucketName)
                        .key(keyName)
                        .build();
    }

    private byte[] getObjectData(String keyName) {
        try {
            GetObjectRequest getObjectRequest = createObjectRequest(keyName);
            ResponseBytes<GetObjectResponse> objectBytes = this.s3.getObjectAsBytes(getObjectRequest);
            
            return objectBytes.asByteArray();

        } catch (S3Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        return null;     
    }

    private byte[] getObjectData(S3Object obj) {
        try {
            GetObjectRequest getObjectRequest = createObjectRequest(obj);
            ResponseBytes<GetObjectResponse> objectBytes = this.s3.getObjectAsBytes(getObjectRequest);
            
            return objectBytes.asByteArray();

        } catch (S3Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        return null;

    }

    private Image createImage(SdkBytes bytes) {
        return Image.builder()
                    .bytes(bytes)
                    .build();
    }

    private DetectLabelsRequest createDetectLabelsRequest(Image img, Float minConfidence) {
        return DetectLabelsRequest.builder()
                    .image(img)
                    .maxLabels(10)
                    .minConfidence(minConfidence)
                    .build();
    }

    private boolean islabelPresent(DetectLabelsResponse response, String term) {
        for (Label label : response.labels()) {
            return label.name().equals(term);
        }

        return false;
    }

    private DetectLabelsResponse detectLabels(S3Object obj, Float minConfidence) {       
        try {
            byte[] data = getObjectData(obj);
            SdkBytes sourceBytes = SdkBytes.fromByteArray(data);
            Image img = createImage(sourceBytes);
            DetectLabelsRequest detectLabelsRequest = createDetectLabelsRequest(img, minConfidence);
            
            return this.rek.detectLabels(detectLabelsRequest);

        } catch (RekognitionException e) {
                System.out.println(e.getMessage());
                System.exit(1);
        } 

        return null;
    }

    public void rekCar(Float minConfidence) throws RekognitionException {
        for (S3Object obj : listBucketObjects()) {
            DetectLabelsResponse response = detectLabels(obj, minConfidence);
        
            if (islabelPresent(response, "Car")) {
                enqueue(obj.key());
                System.out.println("Found: " + obj.key() + ", entered queue.");
            }
        }
        enqueue(endOfStream);
    }

    private CreateQueueRequest createQueueRequest() {
        return CreateQueueRequest.builder()
                    .queueName(this.queueName)
                    .build();
    }

    private String getQueueUrl() {
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                    .queueName(this.queueName)
                    .build();
        return this.sqs.getQueueUrl(getQueueRequest).queueUrl();
    }

    private void enqueue(String msg) {
        try {
            CreateQueueRequest queueRequest = createQueueRequest();
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(this.queueUrl)
                    .messageBody(msg)
                    .messageGroupId(hash(msg))
                    .messageDeduplicationId(msg)
                    .build();          

            this.sqs.sendMessage(sendMessageRequest);
        } catch (SqsException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    private String hash(String msg) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(msg.getBytes());
            byte[] hashBytes = digest.digest();

            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }

    private String dequeue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(this.queueUrl)
                    .waitTimeSeconds(20)
                    .maxNumberOfMessages(1)
                    .build();
            ReceiveMessageResponse receiveResponse = this.sqs.receiveMessage(receiveRequest);
            
            List<Message> messages = receiveResponse.messages();

            if (!messages.isEmpty()) {
                Message msg = messages.get(0);

                String msgString = msg.body();

                deleteMessage(msg);

                return msgString;

            } else {
                System.out.println("No messages available.");
            }

        } catch (SqsException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        return null;
        
    }

    public void poll() {
        boolean polling = true;
        while (polling) {
            String msg = dequeue();
            if (!endOfStream.equals(msg)) {
                byte[] data = getObjectData(msg);
                DetectTextResponse textResponse = detectText(data);
                List<String> textEntries = toString(textResponse);
                System.out.println("Processing: " + msg + ", from queue.");

                if (!textEntries.isEmpty()) {
                    output.put(msg, textEntries);
                }
            } else {
                polling = false;
            }
        }
    }

    private void deleteMessage(Message msg) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build();
    }
    
    private DetectTextRequest createDetectTextRequest(Image img) {
        return DetectTextRequest.builder()
                .image(img)
                .build();
    }

    private DetectTextResponse detectText(byte[] data) {
        try {
            SdkBytes sourceBytes = SdkBytes.fromByteArray(data);
            Image img = createImage(sourceBytes);
            DetectTextRequest textRequest = createDetectTextRequest(img);

            return rek.detectText(textRequest);

        } catch (RekognitionException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        } 

        return null;
    }



    private List<String> toString(DetectTextResponse response) {
        List<String> detectedText = new ArrayList<>();
        for (TextDetection text : response.textDetections()) {
            detectedText.add(text.detectedText());
        }
        return detectedText;

    }

    public void writeOutputToCsv(String fileName) {
        String filePath = this.outputLoc + fileName;
        try {
            FileWriter fileWriter = new FileWriter(filePath);
            CSVWriter csvWriter = new CSVWriter(fileWriter);
            for (Map.Entry<String, List<String>> entry : output.entrySet()) {
                StringJoiner joiner = new StringJoiner(",");
                joiner.add(entry.getKey());
                for (String text : entry.getValue()) {
                    joiner.add(text);
                }
                csvWriter.writeNext(joiner.toString().split(","));
            }
            System.out.println("CSV file created successfully. Location: " + filePath);
            csvWriter.close();
        
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}