package mr.f.demo.data

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.TypeAlias

@TypeAlias("person")
data class Person(
    val firstName: String,
    val lastName: String
) {
    @Id val _id: String? = null
}