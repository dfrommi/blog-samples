package mr.f

import groovy.util.logging.Slf4j
import mr.f.domain.Person
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@Slf4j
@RequestMapping("/persons")
class PersonController {
    @Autowired
    private PersonRepository repository

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    Person getPersonById(@PathVariable String id) {
        log.info("getPersonById with parameter $id")
        repository.findOne(id)
    }

    @RequestMapping(method = RequestMethod.GET)
    List<Person> getAllPersons() {
        log.info("getAllPersons()")
        repository.findAll()
    }

    @RequestMapping(method = RequestMethod.POST)
    Person createPerson(@RequestBody Person newPerson) {
        log.info("createPerson with parameter $newPerson")
        newPerson.id = null
        repository.save(newPerson)
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    Person updatePerson(@PathVariable String id, @RequestBody Person updatedPerson) {
        log.info("updatePerson with parameter $id and $updatedPerson")
        updatedPerson.id = id
        repository.save(updatedPerson)
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    Person removePerson(@PathVariable String id) {
        log.info("removePerson with parameter $id")
        repository.delete(id)
    }

    @RequestMapping(value = "/search/byFirstName/{firstName}", method = RequestMethod.GET)
    Person getPersonByFirstName(@PathVariable String firstName) {
        log.info("getPersonByFirstName with parameter $firstName")
        repository.findByFirstName(firstName)
    }

    @RequestMapping(value = "/headers", method = RequestMethod.GET)
    def getPersonHeaders() {
        log.info("getPersonHeaders")
        repository.findAll().collect {Person Person ->
            [id: Person.id, firstName: Person.firstName, lastName: Person.lastName]
        }
    }
}
