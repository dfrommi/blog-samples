---
posted: 2015-06-25
tags: [spring, spring boot, rest, spring security, mongodb, groovy]
---

# ReST service with Authentication, Spring Data and MongoDB

This post shows how to build a very simple ReST service secured by basic authentication. It is backed by MongoDB and uses Spring Data for database access.

The test program exposes a ReST service to maintain Person data. It can do CRUD as well as some specific search operations. We're going to use the following technologies for that:

- *Gradle* for building
- *Docker* to run MongoDB
- *Spring Boot* to make things very simple
- *Spring Web* to expose ReST service
- *Spring Security* to secure the same using basic authentication
- *Spring Data* for database access

## Build script
First things first, the build script.

```groovy
group 'mr.f'
version '1.0'

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath 'org.springframework.boot:spring-boot-gradle-plugin:1.2.4.RELEASE'
    }
}

apply plugin: 'groovy'
apply plugin: 'spring-boot'

repositories {
    mavenCentral()
}

dependencies {
    compile 'org.codehaus.groovy:groovy-all:2.3.11'
    compile 'org.springframework.boot:spring-boot-starter-web'
    compile 'org.springframework.boot:spring-boot-starter-security'
    compile 'org.springframework.boot:spring-boot-starter-data-mongodb'

    testCompile group: 'junit', name: 'junit', version: '4.11'
}

task wrapper(type: Wrapper) {
    gradleVersion = '2.4'
}
```

Nothing spectacular here. It's the basic setup of a `spring-boot` build script with some dependencies.

## MongoDB
Now let's get our database up and running. Thanks to Docker that's a piece of cake.

```bash
docker run -p 27017:27017 mongo
```

Official MongoDB image is started and default port is exposed. I'm running Docker on a Mac with boot2docker. The host is therefore not localhost and Spring Boot needs to know that. An `application.yml` file is required with following content

```yaml
spring.data.mongodb.host: 192.168.59.103
```

Replace the IP with whatever your boot2docker IP is. If MongoDB is accessible using localhost, then you don't need this file.

## Domain objects
As mentioned in the introduction, the service should manage Person objects. To make things a little more interesting, a reference to an `Address` object is added. I used it to test how MongoDB and Spring Data handle references.

```groovy
@ToString(includeNames = true)
class Person {
    @Id
    String id

    String firstName
    String lastName

    Address address
}
```

```groovy
@ToString(includeNames = true)
class Address {
    String planet
}
```

Having only planet in `Address` seems to be a very broad scope, but to cite my favorite author

> Space is big. Really big. You just won't believe how vastly, hugely, mind-bogglingly big it is. I mean, you may think it's a long way down the road to the chemist, but that's just peanuts to space. *(Douglas Adams, Hitchhiker's Guide to the Galaxy)*

So why add a city or even a street.

## Database Layer
As mentioned earlier, Spring Boot is used for database access. The concept is simple and great: define an interface and follow naming conventions for method names. Spring Boot takes care of the rest.

```groovy
interface PersonRepository extends MongoRepository<Person, String> {
    Person findByFirstName(String firstName)
    List<Person> findByLastName(String lastName)
    List<Person> findByAddressPlanet(String planet)
}
```

`PersonRepository` interface extends `MongoRepository` which adds all CRUD methods. We just have to add methods for specific search operations. Last part of `findByFirstName` and `findByLastName` refer to properties in `Person` class. Spring Boot also determines if we want a single `Person` object or a list of it.

Method `findByAddressPlanet` adds some more magic. It looks into the `address` property of `Person` and uses its `planet` property for comparison. Yes, Spring Boot even follows references to other objects and you still only use method naming conventions.

## Adding security
All ReST service methods should require authentication. A new configuration class is required for that, extending `WebSecurityConfigurerAdapter` and with annotations `@EnableWebSecurity` and `@EnableGlobalMethodSecurity(prePostEnabled = true)`.

```groovy
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests().anyRequest().fullyAuthenticated();
        http.httpBasic();
        http.csrf().disable();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser("user").password("password").roles("USER").and()
                .withUser("admin").password("pwd").roles("ADMIN", "USER")
    }
}
```

Method `configure` ensures that all requests are secured and that basic authentication is used.

Valid users and password are kept in memory and are configured in `configureGlobal` method. In a real-world application you would of course use something else here like database, LDAP or OAuth.

## ReST service
Next step is to encapsulate `PersonRepository` by a ReST service.

```groovy
@RestController
@Slf4j
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/persons")
class PersonController {
    @Autowired
    private PersonRepository repository

    @PreAuthorize("hasRole('ROLE_USER')")
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    Person getPersonById(@PathVariable String id) {
        log.info("getPersonById with parameter $id")
        repository.findOne(id)
    }

    @PreAuthorize("hasRole('ROLE_USER')")
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

    @PreAuthorize("hasRole('ROLE_USER')")
    @RequestMapping(value = "/search/byFirstName/{firstName}", method = RequestMethod.GET)
    Person getPersonByFirstName(@PathVariable String firstName) {
        log.info("getPersonByFirstName with parameter $firstName")
        repository.findByFirstName(firstName)
    }
}
```

Looks like much, but it really isn't. There's one small wrapper per CRUD operation. Depending on the request method, we either list, find, create, update or delete `Person` objects and forward the call to `PersonRepository`.

Method `getPersonByFirstName` is a wrapper for one of our search operations.

Note how we are using `@PreAuthorize` annotation to require ADMIN roles for write operations, but also allow USER role to call read methods.

## Application entry point
Enough talk. Let's run the application. Only two things left, `main` method and test data. And here it is...

```groovy
@SpringBootApplication
@Slf4j
class Application {
    @Autowired
    private PersonRepository repository

    static void main(String[] args) {
        SpringApplication.run(Application.class, args)
    }

    @PostConstruct
    void init() {
        repository.deleteAll()

        Person arthur = new Person(firstName: 'Arthur', lastName: 'Dent', address: new Address(planet: 'Earth'))
        Person trillian = new Person(firstName: 'Trillian', lastName: 'McMillan', address: new Address(planet: 'Earth'))
        Person ford = new Person(firstName: 'Ford', lastName: 'Prefect', address: new Address(planet: 'Betelgeuse 5'))

        log.info("Setting up test data")
        repository.save(arthur)
        repository.save(trillian)
        repository.save(ford)

        println "### findAll() ###"
        repository.findAll().each { println it }
        println ""

        println "### findByFirstName(Arthur) ###"
        println repository.findByFirstName("Arthur")
        println ""

        println "### findByAddressPlanet(Earth) ###"
        repository.findByAddressPlanet("Earth").each { println it }
        println ""
    }
}
```

If you've seen a Spring Boot application before, then `main` method will look familiar. Note that `PersonRepository` can be autowired, though we haven't added any special annotation to it. Another piece of Spring Boot magic.

The `init` method first resets test database, adds some test data and runs a few queries.

## Test run
The application is started with

```bash
gradle bootRun
```

and listens then on port 8080. To get a list of all person objects in database, following `curl` command can be used

```bash
curl admin:pwd@localhost:8080/persons
```

Let's say, id of one object is `558883595ca419a113ee1440`, then you get the specific person with

```bash
curl admin:pwd@localhost:8080/persons/558883595ca419a113ee1440
```

And so on...

## Summary
We created a CRUD ReST service that is backed by a database in only a few lines of code. Thanks to Spring Boot and Docker, we could really concentrate on logic and glue code was not required.

With Spring Boot and MongoDB, we did not even have to configure an OR-mapping.

The only class where some reusable componant seems to be possible is `PersonController`, because same set of operations could be resued for several domain objects. On the other hand, real-world applications are usually not just delegating to `Respository` objects, but instead start some kind of object-specific workflow.
