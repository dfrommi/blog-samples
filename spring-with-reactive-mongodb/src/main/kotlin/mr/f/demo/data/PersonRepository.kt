package mr.f.demo.data

import org.springframework.data.repository.reactive.ReactiveCrudRepository

interface PersonRepository: ReactiveCrudRepository<Person, String>