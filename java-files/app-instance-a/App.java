package edu.njit.pg262;

import edu.njit.pg262.Instance;

import software.amazon.awssdk.regions.Region;

public class App {
    public static void main(String[] args) {
        final String usage = """

                Usage:    <bucketName> <queueName> <minConfidence>     
               
                Where:    bucketName - The Amazon S3 bucket from which objects are read.\s
                          queueName - The Amazon SQS used for communication between EC2 instances.\s
                          minConfidence - The minimum confidence score for detected cars.

                """;

        if (args.length != 3) {
            System.out.println(usage);
            System.exit(1);
        }

        String bucketName = args[0];
        String queueName =  args[1];
        String minConfidence = args[2];

        Region region = Region.US_EAST_1;

        Instance instanceA = new Instance(region, bucketName, queueName);
        instanceA.connect();
        instanceA.rekCar(Float.parseFloat(minConfidence));
        instanceA.disconnect();
        }
}