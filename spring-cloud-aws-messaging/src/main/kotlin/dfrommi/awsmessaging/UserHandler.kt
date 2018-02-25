package dfrommi.awsmessaging

import org.slf4j.LoggerFactory
import org.springframework.cloud.aws.messaging.config.annotation.NotificationMessage
import org.springframework.cloud.aws.messaging.config.annotation.NotificationSubject
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener

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
