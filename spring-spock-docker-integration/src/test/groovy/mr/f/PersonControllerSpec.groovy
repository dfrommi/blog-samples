package mr.f

import mr.f.domain.Address
import mr.f.domain.Person
import spock.lang.Specification

class PersonControllerSpec extends Specification {
    private PersonRepository repository = Mock(PersonRepository)
    private PersonController controller = new PersonController(repository: repository)
    private Person testPerson = new Person(firstName: "John", lastName: "Doe", address: new Address(planet: "Earth"))

    def "GetPersonById with existing id returns Person object"() {
        when:
            Person result = controller.getPersonById("1234")
        then:
            1 * repository.findOne("1234") >> testPerson
            with(result) {
                firstName == "John"
                lastName == "Doe"
            }
    }

    def "GetPersonById with invalid id returns null"() {
        when:
            Person result = controller.getPersonById("XXXX")
        then:
            1 * repository.findOne("XXXX") >> null
            result == null
    }

    def "createPerson sets id to null"() {
        given:
            Person capturedPerson
            testPerson.id = "1234"
        when:
            controller.createPerson(testPerson)
        then:
            1 * repository.save({Person p -> (capturedPerson = p).id == null})
            capturedPerson.firstName == "John"
    }

    def "updatePerson overwrites id in Person object"() {
        given: "a person with id 1234"
            testPerson.id = "1234"
        when: "is updated using id parameter 'abcd'"
            controller.updatePerson("abcd", testPerson)
        then: "person gets stored with id 'abcd'"
            1 * repository.save({Person p -> p.id == "abcd"})
    }
}
