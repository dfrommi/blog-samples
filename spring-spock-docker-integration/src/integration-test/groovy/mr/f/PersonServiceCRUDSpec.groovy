package mr.f

import mr.f.domain.Address
import mr.f.domain.Person
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.TestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Unroll

class PersonServiceCRUDSpec extends RestIntegrationBase {
    String getBasePath() {"persons/"}

    @Autowired
    PersonRepository personRepo

    @Shared
    String currentUserId

    def "remove all users from database"() {
        given:
            personRepo.deleteAll()
        when:
            List<Person> allPersons = personRepo.findAll()
        then:
            allPersons.isEmpty()
    }

    def "create Arthur Dent"() {
        given:
            Person arthur = new Person(firstName: 'Arthur', lastName: 'Dent', address: new Address(planet: 'Earth'))
            RequestEntity<Person> request = RequestEntity.post(serviceURI()).body(arthur)
        when:
            ResponseEntity<Person> response = restTemplate.exchange(request, Person)
            List<Person> allPersons = personRepo.findAll()
        then:
            response.statusCode == HttpStatus.OK
            allPersons.size() == 1
            with(allPersons.get(0)) {
                id != null
                [firstName, lastName, address.planet] == ["Arthur", "Dent", "Earth"]
            }
    }

    def "retrieve Arthur by first name"() {
        given:
            RequestEntity request = RequestEntity.get(serviceURI("search/byFirstName/Arthur")).build()
        when:
            ResponseEntity<Person> response = restTemplate.exchange(request, Person)
            Person result = response.getBody()
            currentUserId = result.id
        then:
            response.statusCode == HttpStatus.OK
            result.firstName == "Arthur"
            result.id != null
    }

    def "get all persons"() {
        given:
            RequestEntity request = RequestEntity.get(serviceURI()).build()
        when:
            ResponseEntity<Person[]> response = restTemplate.exchange(request, Person[])
            Person[] result = response.getBody()
        then:
            response.statusCode == HttpStatus.OK
            result.size() == 1
    }

    def "Arthur now lives on Lamuella"() {
        given:
            Person arthur = new Person(firstName: 'Arthur', lastName: 'Dent', address: new Address(planet: 'Lamuella'))
            RequestEntity<Person> request = RequestEntity.put(serviceURI(currentUserId)).body(arthur)
        when:
            ResponseEntity<Person> response = restTemplate.exchange(request, Person)
            Person updatedPerson = personRepo.findOne(currentUserId)
            int numberOfPersons = personRepo.count()
        then:
            response.statusCode == HttpStatus.OK
            numberOfPersons == 1
            with(updatedPerson) {
                [firstName, lastName, address.planet] == ["Arthur", "Dent", "Lamuella"]
            }
    }

    def "remove Arthur from database"() {
        given:
            RequestEntity request = RequestEntity.delete(serviceURI(currentUserId)).build()
        when:
            ResponseEntity response = restTemplate.exchange(request, Object)
            int numberOfPersons = personRepo.count()
        then:
            response.statusCode == HttpStatus.OK
            numberOfPersons == 0
    }
}