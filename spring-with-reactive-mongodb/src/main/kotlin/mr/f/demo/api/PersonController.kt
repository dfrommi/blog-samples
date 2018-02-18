package mr.f.demo.api

import mr.f.demo.data.PersonRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController()
@RequestMapping("/persons")
class PersonController(private val personRepository: PersonRepository) {
    @GetMapping("/")
    fun findAll(): Flux<PersonResponse> =
            personRepository.findAll().map { PersonResponse("${it.firstName} ${it.lastName}") }
}

class PersonResponse(val name: String)