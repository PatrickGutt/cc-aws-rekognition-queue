package edu.njit.pg262;

import edu.njit.pg262.Instance;

import software.amazon.awssdk.regions.Region;

public class App {
    public static void main(String[] args) {
        final String usage = """

                Usage:    <bucketName> <queueName>     
               
                Where:    bucketName - The Amazon S3 bucket from which objects are read.\s
                          queueName - The Amazon SQS used for communication between EC2 instances.\s

                """;

        if (args.length != 2) {
            System.out.println(usage);
            System.exit(1);
        }

        String bucketName = args[0];
        String queueName =  args[1];

        Region region = Region.US_EAST_1;

        Instance instanceB = new Instance(region, bucketName, queueName);
        instanceB.connect();        
        instanceB.poll();
        instanceB.disconnect();
        instanceB.writeOutputToCsv("cars-with-text.csv");
        }
}