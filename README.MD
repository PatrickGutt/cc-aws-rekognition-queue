## Overview

This project employs AWS SQS, Rekognition, and S3 buckets across two EC2 instances:

- `Instance-A` utilizes AWS Rekognition to analyze input images located in an S3 bucket. Images identified by AWS Rekognition as containing cars are then sent to a common SQS queue.
- `Instance-B` retrieves the images from the SQS queue and outputs the file names of images to a `.csv` file that are recognized with a confidence level of 90% or higher.

`java-files` directory contains the respective EC2 instance java programs. 

`pa1-instance-a` and `pa1-instance-b` directories are snapshots of the file system on each respective EC2 instance. 

## AWS Academy Configuration

### Accessing AWS Academy

To access AWS Academy, follow these steps:

1. **Launch AWS Academy** using your login credentials.

2. Navigate to `Modules > AWS Academy Learner Lab > Launch AWS Academy Learner Lab` to launch the learner lab.

3. Click on **Start Lab** to initialize a session.

4. Connect to the AWS Console by clicking **AWS** above the terminal.

#### AWS Details

When utilizing AWS SDKs, you will need access keys provided by AWS. These keys include your AWS CLI credentials and SSH `.pem` private key, which are unique to your specific AWS account.

#### Accessing AWS CLI Credentials

To access your AWS CLI credentials, follow these steps:

1. Locate and click **AWS Details** on the AWS Academy Learner Lab page.

2. Click on **Show** to display your AWS CLI credentials.

3. Copy and paste these details into the `~/.aws/credentials` file after creating your EC2 instances.

#### Accessing Your SSH PEM Key

To access your SSH PEM key, follow these steps:

1. While keeping your AWS Details open, click on **Download PEM** to retrieve and download your SSH `.pem` key.

2. Store it in a known location on your computer.

#### Handling PEM Key

To access each specific EC2 instance, you will need a public key that corresponds to your private key. You can generate it using the following command in your command prompt on Windows:

1. Open the Command Prompt on Windows.

2. Navigate to the directory where the `.pem` private key was downloaded.

3. Enter the command:
   ```sh
   ssh-keygen -y -f yourkey.pem > yourkey.pub
    ```

### EC2 Instances

Once connected to the AWS console, navigate to the EC2 Dashboard. This is where EC2 instances can be created and configured.

#### Specifying Key Pairs

- From the EC2 Dashboard, navigate to `Network & Security > Key Pairs`.

- Under the Actions dropdown menu, select **Import key pair**.

  - Enter a name for your key pair.

  - In the Public key contents, paste the contents of your `.pub` key, which was generated previously.

    - Note: Alternatively, you can select **Browse** and navigate to your public key on your computer, if downloaded.

  - Select **Import** to import the key pair into your AWS account.

#### Specifying a Security Group

- From the EC2 Dashboard, navigate to `Resources > Security Groups` to open a menu to create a new security group.

- Select **Create security group** to open the creation menu.

- Enter a security group name and description.

- Add HTTPS, HTTP, and SSH rules using the Type dropdown menu and selecting **Add rule**.

- Select **My IP** using the Destination dropdown menu to create a secure security group where only your IP can access the EC2 instances.

- Repeat this process for Inbound rules. You can add additional IP addresses to the security group when needed.

  - Note: To configure EC2 instances and download required packages, all rules were set to accept all IP addresses temporarily.

#### Launching an EC2 Instance

- From the EC2 Dashboard, navigate to `Instances`. Select **Launch instances** to begin creating an EC2 instance.

- Enter a name to identify the instance.

- Select **Amazon Linux** as the AMI.

- Select the previously created key pair under the Key pair dropdown menu.

- For Network Settings, choose **Select existing security group** and select the previously created security group from the dropdown menu.

- All other settings can be left as default.

#### Connecting to an EC2 Instance using Visual Studio Code on Windows

To work with EC2 instances in VSCode, follow these steps:

- **Install the Remote-SSH Extension**

- **Configure the SSH Configuration File**:

  - The SSH configuration file is usually located at `~/.ssh/config` or `/users/ssh/config`.

  - Open this file in Visual Studio Code.

  - Add an entry for an EC2 instance, specifying the IP address, username, and the path to the downloaded private `.pem` key. An example is as follows:
    ```plaintext
    Host Instance-A
      HostName 54.196.105.103
      User ec2-user
      IdentityFile ~/Downloads/AWS-Academy.pem

    Host Instance-B
      HostName 54.80.223.156
      User ec2-user
      IdentityFile ~/Downloads/AWS-Academy.pem
    ```
    - Note: If your EC2 instance is restarted or stopped, its public IP address might change. Remember to update the IP address in your SSH configuration file.

- Once installed, use the shortcut `Ctrl+Shift+P` to open the search menu and search for “Remote-SSH: Connect to Host...” and follow the prompts to connect to your desired EC2 instance.

#### EC2 Configuration

Once connected to your desired EC2 instance, follow these steps:

- Run the command in the terminal:
  ```sh
  aws configure
  ```
  This creates a .aws directory and the required credentials file in your EBS storage. This 
command will prompt you to enter your AWS Access Key, AWS Secret Access Key, default 
region, and default output format.

- Follow the prompts by entering the AWS Access Key and AWS Secret Access Key from the AWS 
CLI credentials from the **AWS Details** located on the AWS Academy Learning Lab page. 

- Leave the default region and default output format blank and press enter to specify them as none 
(default). 

- Once you provide this information, it will create the .aws directory in the home/ec2-user 
directory.  
    - Once instance was created and developed. An AMI was generated off this EC2 
instance to create a second instance using the following command in terminal:
        ```sh
        aws ec2 create-image --instance-id i-04f17b0f0b045b289 --name pa1-ami --no-reboot 
        ```

### Simple Queue Service (SQS)

- Navigate to the Amazon SQS page and select Create queue to being creating a SQS queue. 
- Choose FIFO in the Details section and enter a name to uniquely identify the queue in your program. 
- All other settings can be left as default.

### Additional Configurations

#### AWS SDK

AWS SKD will need to be configured for Java to communicate with the various AWS services. This can be 
done using Apache Maven. To install Maven and create a project, follow these steps: 
- Run the command in the terminal: 
    ```sh
    sudo yum install maven 
    ```
    This will install Maven on your EC2 instance from the AWS repository.   
    - Note. Running this command will also install Java 17, thus running `sudo yum install 
java` is not required.  

- Once installed, run the command in the terminal: 
    ```sh
    mvn -B archetype:generate \ 
    -DarchetypeGroupId=software.amazon.awssdk \ 
    -DarchetypeArtifactId=archetype-lambda -Dservice=s3 -Dregion=US_EAST_1 \ 
    -DarchetypeVersion=2.25.3 \ 
    -DgroupId=edu.njit.pg262 \ 
    -DartifactId=pa1 
    ```
    This will create a Maven project that can be used with AWS SDK.  
- Once the project is created, open the pom.xml file in the project directory to continue 
configuring AWS SDK.  

- Add the AWS SDK and your required services to the `<dependencies>` section. For example,
    ```
    <dependencyManagement> 
    <dependencies> 
        <dependency> 
        <groupId>software.amazon.awssdk</groupId> 
        <artifactId>bom</artifactId> 
        <version>2.25.3</version> 
        <type>pom</type> 
        <scope>import</scope> 
        </dependency> 
    </dependencies> 
    </dependencyManagement> 
    
    <dependency> 
        <groupId>software.amazon.awssdk</groupId> 
        <artifactId>ec2</artifactId> 
    </dependency> 
    <dependency> 
        <groupId>software.amazon.awssdk</groupId> 
        <artifactId>s3</artifactId> 
    </dependency> 
    <dependency> 
        <groupId>software.amazon.awssdk</groupId> 
        <artifactId>sqs</artifactId> 
    </dependency> 
    <dependency> 
        <groupId>software.amazon.awssdk</groupId> 
        <artifactId>rekognition</artifactId> 
    </dependency>
    ```
    - If you receive a `java.lang.NoClassDefFoundError`, it is likely a dependency or 
plugin issue.

## Application

### Compiling

- Navigate to the directory containing the Maven project.  

- Run the command in the terminal:  
    ```sh
    mvn clean package 
    ```
    This command will compile the Java source files and download any necessary dependencies from the 
    Maven repository. It will also run any tests, and package your application into a JAR file in the target 
    directory. 

### Executing the Program

- Navigate to the home directory of the EC2 instance. 

- Run the command in the terminal: 
    ```bash
    sudo nano run-app.sh
    ``` 
    This will open the nano text editor to create a bash script file.  
- Copy and paste the following into the script file for Instance-A: 
    ```
    cd /home/ec2-user/pa1/target 
    java -jar app.jar njit-cs-643 pa1-queue.fifo 90F
    ```
- Copy and paste the following into  the script file for Instance-B: 
    ```sh
    cd /home/ec2-user/pa1/target 
    java -jar app.jar njit-cs-643 pa1-queue.fifo 
    ```
- Make the script executable by running the command in the terminal: 
    ```sh
    sudo chmod +x run-app.sh 
    ```
- Execute the programming by running the command in the terminal from the home directory: 
    ```sh
    run-app.sh 
    ``` 
    This is used for both EC2 instances. The output is stored in the output file in the pa1 project directory.  

## References

- https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/signup-create-iam-user.html#java-dg-java-env 
- https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/creating-sqs-standard-queues.html 
- https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-project-maven.html  
- https://docs.aws.amazon.com/rekognition/latest/dg/text-detecting-text-procedure.html 
- https://docs.aws.amazon.com/rekognition/latest/dg/labels-detect-labels-image.html 
- https://github.com/aws/aws-sdk-java-v2 
- https://labs.vocareum.com/web/3057517/2523670.0/ASNLIB/public/docs/lang/en-us/README.html