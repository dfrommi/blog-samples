package dfrommi.awsmessaging

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application {
    @Bean
    fun userHandler(amazonSns: AmazonSNS) = UserHandler(NotificationMessagingTemplate(amazonSns))
}

// Create topic, queue and subscription.
// Notifications to SNS are forwarded to SQS queue
fun prepareMessagingEnvironment(awsConfig: LocalstackConfig) {
    val amazonSns = awsConfig.amazonSNS()
    val amazonSqs = awsConfig.amazonSQS()

    createSubscription(amazonSns, amazonSqs, "event-user-registration", "user-create")
    createSubscription(amazonSns, amazonSqs, "event-user-updated", "user-audit-log")
}

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

fun main(args: Array<String>) {
    // Execute before Spring start, otherwise @SqsListener would not find queue
    prepareMessagingEnvironment(LocalstackConfig())

    SpringApplication.run(Application::class.java, *args)
}
