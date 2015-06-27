package mr.f

import mr.f.domain.Person
import org.springframework.data.mongodb.repository.MongoRepository;

interface PersonRepository extends MongoRepository<Person, String> {
    Person findByFirstName(String firstName)
    List<Person> findByLastName(String lastName)
    List<Person> findByAddressPlanet(String planet)
}
