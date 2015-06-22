package mr.f.domain

import groovy.transform.ToString
import org.springframework.data.annotation.Id

@ToString(includeNames = true)
class Person {
    @Id
    String id

    String firstName
    String lastName

    Address address
}
