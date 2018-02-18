---
posted: 2018-02-18
tags: [spring, kotlin, mongodb, reactive]
comments: 16
---

# Spring Boot 2 and reactive MongoDB example

Spring Boot 2 is based on Spring 5 and has full reactive support. [Here](https://github.com/dfrommi/blog-samples/tree/master/spring-with-reactive-mongodb) is a small example app that exposes a Rest endpoint to retrieve data from MongoDB, reactive from one end to the other.

The example app is using an embedded Mongo database running at `localhost:27017`.

## Road to Reactive
To use the reactive non-blocking programming model in Spring 5, you have to

- use `Mono<T>` (for single objects) or `Flux<T>` for streams instead of the object or collection directly
- replace the imperative programming model by stream-modification methods, like `map`, `flatMap` etc.

### Reactive Spring Data
For reactive MongoDB support, use dependency `org.springframework.boot:spring-boot-starter-data-mongodb-reactive` instead of `org.springframework.boot:spring-boot-starter-data-mongodb`. 

Now you can make use of MongoDB's reactive driver. The `ReactiveCrudRepository` is similar to the well-known `CrudRepository` base interface, but uses return types `Mono` and `Flux` instead. 

For example

```kotlin
interface PersonRepository: ReactiveCrudRepository<Person, String>
```

### Reactive Spring Web
Reactive Spring Web is using the same Controller and PathMapping annotations, just the return type of controller methods is again `Mono` or `Flux`. 

In combination with the `ReactiveCrudRepository`, writing such a controller method is quite easy. In the following example, the model object from database is transformed to an API DTO object, just to show how stream processing methods are applied.

```kotlin
@RestController()
@RequestMapping("/persons")
class PersonController(private val personRepository: PersonRepository) {
    @GetMapping("/")
    fun findAll(): Flux<PersonResponse> =
            personRepository.findAll().map { PersonResponse("${it.firstName} ${it.lastName}") }
}

class PersonResponse(val name: String)
``` 

The API is called with `curl http://localhost:8080/persons/`.

## Tips & Tricks
### Refactor-safe serialization
When Spring Data serializes objects for MongoDB, a `_class` property is added to the document containing the fully qualified class name. When the document is read from DB again, Spring Data knows what object to create.
Unfortunately this mechanism breaks when the class is renamed or moved to another package. Previously stored documents have to be migrated.

To avoid that, Kotlin's `@TypeAlias` annotation can be used. If present, the value of the type-alias is used instead of the class name.

```kotlin
@TypeAlias("person")
data class Person(
    val firstName: String,
    val lastName: String
) {
    @Id val _id: String? = null
}
```  

### Initialization
During startup of the application, some data is written to database.

```kotlin
@Bean
open fun init(repository: PersonRepository) = CommandLineRunner {
    repository.deleteAll()
            .then(repository.save(Person("Arthur", "Dent")))
            .then(repository.save(Person("Ford", "Prefect")))
            .subscribe()
}

```

It is very important to call `subscribe` at the end of the stream definition. Streams without subscribers are not executed.

More info on Spring reactive programming can be found [here](https://docs.spring.io/spring/docs/5.0.0.BUILD-SNAPSHOT/spring-framework-reference/htmlsingle/#web-reactive).
