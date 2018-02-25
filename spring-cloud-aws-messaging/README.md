---
posted: 2018-02-24
tags: [spring, spring cloud, aws, kotlin]
comments: 17
---

# Spring Cloud AWS Messaging with LocalStack

[Spring Cloud's AWS project](https://cloud.spring.io/spring-cloud-aws/) integrates Spring applications into Amazon's infrastructure seamlessly. The messaging part provides easy access to *Simple Notifications Service (SNS)* and *Simple Queue Service (SQS)*. To be independent of the real AWS infrastructure on development and automated testing environments, [LocalStack](https://github.com/localstack/localstack) can be used. It provides a collection of fake AWS service implementations running in a docker container.

This article shows how to use LocalStack with Spring Cloud's AWS messaging.

## Running the example
A full example project is available [here](https://github.com/dfrommi/blog-samples/tree/master/spring-cloud-aws-messaging). It consumes SQS messages and publishes SNS notifications. 

Execute it with the following steps:

1. Start LocalStack: `docker-compose up -d`
1. Start the Spring Boot application: `./gradlew bootRun`.
1. Send a notification to the SNS topic `event-user-registration` with a user's full name as message payload

    ```bash
    export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
    export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
    export AWS_DEFAULT_REGION=us-east-1
    
    aws --endpoint-url=http://localhost:4575 sns publish --topic-arn "arn:aws:sns:us-east-1:123456789012:event-user-registration" --subject "user-registeration.ios" --message "Arthur Dent"
    ```
1. User creation and audit log message show up in the Spring Boot console log

## SNS and SQS introduction
Just very quickly, how does SNS and SQS work.

Messages (called notifications) are sent to SNS topics. Topics are identified by name and have to be set up before you can use them.

Notifications are not consumed directly. Instead, you configure one or more subscriptions to forward the message. SNS supports several subscriptions types, like SQS, http(s) call, Apple/Android push notifications and more.

This setup offers a lot of flexibility. 
Notifications can be consumed by multiple applications using the technology they prefer. Applications also don't have to take care of processing the notification only once. A problem that scalable applications consisting of multiple instances and thus multiple consumers would have, were they consuming SNS notifications directly.  

## Spring Cloud AWS
Given that the project is already set up for Spring Cloud, the dependency `org.springframework.cloud:spring-cloud-starter-aws-messaging`  integrates the application with AWS.

### Pub/Sub
The example below shows how to consume SQS messages and how to publish SNS notifications

```kotlin
class UserHandler(private val notificationMessagingTemplate: NotificationMessagingTemplate) {
    private val log = LoggerFactory.getLogger(UserHandler::class.java)

    @SqsListener(value = ["user-create"])
    fun createUser(@NotificationSubject subject: String?, @NotificationMessage message: String) {
        val (firstName, lastName) = message.split(' ')
        val user = User(firstName, lastName, firstName.toLowerCase() + "-" + lastName.toLowerCase())

        log.info("Created user: $user [$subject]")

        notificationMessagingTemplate.sendNotification("event-user-updated", user, "user.new")
    }

    @SqsListener(value = ["user-audit-log"])
    fun logUserEvent(@NotificationSubject subject: String, @NotificationMessage person: User) {
        log.info("Audit log: $person [$subject]")
    }
}

data class User(val firstName: String, val lastName: String, val username: String)
```

The `@SqsListener` annotation registers SQS message listeners. The `@NotificationMessage` parameter contains the payload of the message. 

Method `createUser` expects a simple String, but JSON support with automatic serialization and deserialization works out of the box, as shown in the `logUserEvent` method.

Notifications are published with the `sendNotification` method of `notificationMessagingTemplate`. Parameters are topic name, payload and subject.

### Infrastructure configuration
Topics, queues and subscriptions have to be configured before Spring is setting up SQS listeners. If the queue in the `@SqsListener` annotation doesn't exist during Spring initialization, the listener is just not set up. Startup is not failing in that case!

The configuration could be done manually in the AWS console, but the example project is using the AWS Java SDK to prepare the infrastructure. It's executed before Spring Boot startup to make sure that everything is ready when needed.

The following function creates topic, queue and a subscription.

```kotlin
fun createSubscription(amazonSns: AmazonSNS, amazonSqs: AmazonSQS, topicName: String, queueName: String) {
    // Create topic
    val createdTopic = amazonSns.createTopic(topicName)

    //Create queues
    val createdQueue = amazonSqs.createQueue(queueName)

    val queueArn = amazonSqs.getQueueAttributes(createdQueue.queueUrl, mutableListOf("QueueArn")).attributes["QueueArn"]
    val existingSubscriptions = amazonSns.listSubscriptions().subscriptions

    // link topic to queue unless subscription is already existing
    if (existingSubscriptions.none { it.endpoint == queueArn }) {
        amazonSns.subscribe(createdTopic.topicArn, "sqs", queueArn)
    }
}
```  

## LocalStack
[LocalStack](https://github.com/localstack/localstack) provides a bunch of fake AWS service implementations, each accessible on `localhost` at different ports. By default, SNS is listening on port `4575` and SQS on `4576`. A docker-compose file to run LocalStack is included in the example project.

### How to use
To use the AWS fake service, the endpoint has to be set on clients (AWS CLI or AWS Java SDK).

AWS CLI has parameter `--endpoint-url` for that purpose, for example 
```bash
aws --endpoint-url=http://localhost:4575 sns list-topics
```

[This page](https://lobster1234.github.io/2017/04/05/working-with-localstack-command-line/) has a great overview of how to use AWS CLI with LocalStack. 

In Spring Cloud AWS, the application has to expose custom client beans per service, as done in the configuration for SNS and SQS

```kotlin
@Configuration
@Profile("!prod")
class LocalstackConfig {
    val credentialsProvider: AWSCredentialsProvider = AWSStaticCredentialsProvider(AnonymousAWSCredentials())

    @Bean
    fun amazonSNS(): AmazonSNS = AmazonSNSClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("http://localhost:4575", "us-east-1"))
            .withCredentials(credentialsProvider)
            .build()

    @Bean
    fun amazonSQS(): AmazonSQS = AmazonSQSAsyncClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("http://localhost:4576", "us-east-1"))
            .withCredentials(credentialsProvider)
            .build()
}
```

The configuration is only applied if the profile is not `prod` to use LocalStack in development and testing environments, but not on production. This could be improved by trying to detect if the application is running in AWS and apply the configuration only if it's not.

LocalStack doesn't require authentication, so we can just use `AnonymousAWSCredentials`.

The same pattern can most likely be applied to other services, though I haven't tested any.

### Pitfalls
Spring Cloud AWS assumes that it's running on EC2 and tries to get parameters like the AWS region from the environment. If it can't determine the region, the application startup will fail. To avoid that, the region is set manually in `application.yml`.

LocalStack doesn't require authentication, but AWS CLI is not that relaxed. It's using a credentials provider chain and tries to find authentication parameters at several places, for example in `~/.aws/credentials` or environment variables. If nothing is found, the command fails. The easy fix is to provide fake credentials, for example
```bash
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
export AWS_DEFAULT_REGION=us-east-1
```

## Improvements
For easy integration of LocalStack into Spring Cloud AWS and the AWS Java SDK, an auto-configuration module would be nice. The `@Configuration` would be applied if profile `localstack` is active and provide custom client beans configured for LocalStack access, ideally for all services available on LocalStack.
