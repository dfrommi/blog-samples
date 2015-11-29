---
posted: 2015-07-11
tags: [spring boot, docker, gradle, spock, testing]
---

# Integration testing of Spring Boot app with Spock and Docker

This post shows how to do integration testing of a ReST service as part of *Gradle* build process, using *Spock* to implement the test and *Docker* to make required database available. It's build upon the example project described in [my previous blog post](http://www.frommknecht.net/spring-rest-mongodb/).

Following technologies are used:

- ReST service is implemented with *Spring Boot*
- Database is *MongoDB* running in *Docker* container
- *Gradle* used to build and execute integration tests
- Integration tests are implemented in *Spock*

## The plan
In [my previous blog post](http://www.frommknecht.net/spring-rest-mongodb/), a ReST service has been implemented to perform CRUD operations on Person objects. Now we add integration tests to the project that are interacting with the service and verify its behavior.

During integration test, the real application has to be running and database has to be available. All that should not interfere with real application and database and it should be part of build process.

By executing command `gradle integrationTest`, the following steps should be performed:

1. Start Docker container hosting *MongoDB*. It should only be used by integration tests and not shared with application database.
2. Start the application
3. Execute integration tests
4. Stop application
5. Stop Docker container with *MongoDB*. This should be done even if tests are failing.

## Add integration testing to gradle build
To add integration testing capabilities to `gradle` build, I just followed [this guide](http://www.petrikainulainen.net/programming/gradle/getting-started-with-gradle-integration-testing/), but I did not define a `java` source set, but instead one for `groovy`. Some minor additions were made, but we'll come to that later.

## The build script
Without further ado, let's have a look at the build `gradle` build script:

```groovy
group 'mr.f'
version '1.0'

def DOCKER = '/usr/local/bin/docker'
def DOCKER_GROUP = 'docker'

def DB_ENV = [:]
DB_ENV['AppDB'] = [port: 27017, container: 'appDB']
DB_ENV['IntegrationDB'] = [port: 29017, container: 'integrationDB']

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

sourceSets {
    integrationTest {
        groovy {
            compileClasspath += main.output + test.output
            runtimeClasspath += main.output + test.output
            srcDir file('src/integration-test/groovy')
        }
        resources.srcDir file('src/integration-test/resources')
    }
}

configurations {
    integrationTestCompile.extendsFrom testCompile
    integrationTestRuntime.extendsFrom testRuntime
}

dependencies {
    compile 'org.codehaus.groovy:groovy-all:2.3.11'
    compile 'org.springframework.boot:spring-boot-starter-web'
    compile 'org.springframework.boot:spring-boot-starter-security'
    compile 'org.springframework.boot:spring-boot-starter-data-mongodb'

    testCompile 'org.spockframework:spock-core:1.0-groovy-2.3'
    integrationTestCompile 'org.spockframework:spock-spring:1.0-groovy-2.3'
    integrationTestCompile 'org.springframework:spring-test'
}

tasks.withType(Test) {
    reports.html.destination = file("${reporting.baseDir}/${name}")
}

task integrationTest(type: Test) {
    testClassesDir = sourceSets.integrationTest.output.classesDir
    classpath = sourceSets.integrationTest.runtimeClasspath
    include '**/*Spec.*'
    systemProperty 'spring.profiles.active', 'integration'
    outputs.upToDateWhen { false }
}

// Create one start and one stop Task per DB environment
DB_ENV.each { envName, envProps ->
    tasks.create(name: "start$envName", type: Exec, group: DOCKER_GROUP) {
        commandLine DOCKER, 'run', '-p', "${envProps.port}:27017", '--name', "${envProps.container}", '-d', 'mongo'
    }

    tasks.create(name: "stop$envName" , group: DOCKER_GROUP) << {
        exec { commandLine DOCKER, 'stop', "${envProps.container}" }
        exec { commandLine DOCKER, 'rm', "${envProps.container}" }
    }
}

// Integration test automatically starts and stop docker container with DB
integrationTest.dependsOn startIntegrationDB
integrationTest.finalizedBy stopIntegrationDB

task wrapper(type: Wrapper) {
    gradleVersion = '2.4'
}
```

It's mostly the initial build script with added integration test functionality. But there are some important additions, so let's go through them:

```groovy
integrationTestCompile 'org.spockframework:spock-spring:1.0-groovy-2.3'
integrationTestCompile 'org.springframework:spring-test'
```
The dependency `spring-test` is required to add testing capabilities for Spring Boot. It contains annotations that we'll see later and testing helper classes. We're using Spock instead of jUnit, so we need `spock-spring` as a bridge between `spring-test` and Spock.

```groovy
include '**/*Spec.*'
```
Only test classes with pattern `**/*Spec.*` are used for testing. It excludes helper and base classes.

```groovy
systemProperty 'spring.profiles.active', 'integration'
```
Spring Boot's profile is set to `integration`. This way, we can define specific properties for integration testing in file `application-integration.yml`. We're using this file to set the application port and MongoDB host information.

```groovy
DB_ENV.each { envName, envProps ->
    tasks.create(name: "start$envName", type: Exec, group: DOCKER_GROUP) {
        commandLine DOCKER, 'run', '-p', "${envProps.port}:27017", '--name', "${envProps.container}", '-d', 'mongo'
    }

    tasks.create(name: "stop$envName" , group: DOCKER_GROUP) << {
        exec { commandLine DOCKER, 'stop', "${envProps.container}" }
        exec { commandLine DOCKER, 'rm', "${envProps.container}" }
    }
}
```
This is maybe the most interesting part of the build script. We're dynamically creating start and stop tasks for several docker containers hosting MongoDB databases.
The map `DB_ENV` contains the configuration of MongoDB containers that should be exposed. In this example, tasks `startAppDB`, `stopAppDB`, `startIntegrationDB` and `stopIntegrationDB` are created. *AppDB* is exposing port 27017 while *IntegrationDB* uses port 29017. This allows us to use independent databases per purpose.

```groovy
integrationTest.dependsOn startIntegrationDB
integrationTest.finalizedBy stopIntegrationDB
```
These lines take care that integration test database is started before integration test are running and that it is stopped after tests were executed. The task specified by `finalizedBy` is even executed if `integrationTest` task fails.

At execution of `gradle integrationTest`, then following things are happening:

1. `startIntegrationDB` is executed. MongoDB is then accessible at port 29017.
2. All tests matching pattern `**/*Spec.*` in `integration-test` directory are executed.
3. Regardless of the result of step (2), task `stopIntegrationDB` is executed.

## Base Class for Integration Tests
After we took care of adding everything to the build script, we need some tests.

A common base class helps reducing redundant code and annotations.

```groovy
@ContextConfiguration(loader = SpringApplicationContextLoader.class, classes = [Application.class] )
@WebIntegrationTest
@Stepwise
class RestIntegrationBase extends Specification {
    @Value('${local.server.port}')
    int port

    RestTemplate restTemplate = new TestRestTemplate("admin", "pwd")

    String getBasePath() { "" }

    URI serviceURI(String path = "") {
        new URI("http://localhost:$port/${basePath}${path}")
    }
}
```

Annotations `@ContextConfiguration` and `@WebIntegrationTest` are required for `Spock` to play nicely together with Spring Boot. The `@Stepwise` annotation is specific to `Spock` and ensures that tests are executed in the order they are specified in test class. My integration tests are building upon each other, so this behavior is required.

Apart from that, we're mostly simplifying access to the ReST service. The port that integration test app is running on is injected and a `RestTemplate` is offered with grant-all permission.

## The integration test
After prepation work, we're ready to implement integration tests. The following class tests behavior of `Person` ReST service. It performs a full CRUD flow, with each test building upon the result of the previous one.

Tests are calling the corresponding ReST service method and verify return code and - if applicable - the return value. In addition, `PersonRepositoy` is used to verify that data is really stored in database.

```groovy
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
```

## Authentication test
Previous test verifies behavior using admin user. But access to service is restricted based on user's role. The following integration test checks authentication restrictions of `Person` service for some services. It purely concentrates on testing authentication, not behavior. Return values are ignored.

```groovy
class PersonServiceAuthenticationSpec extends RestIntegrationBase {
    String getBasePath() {"persons/"}

    @Shared
    def ROLE_TO_USER = [
            NO_ROLE: [name: null, password: null],
            USER:    [name: 'user', password: 'password'],
            ADMIN:   [name: 'admin', password: 'pwd']]

    @Unroll("calling #endpoint with user #user should return status #status")
    def "test authentication of #endpoint"() {
        given:
            RestTemplate restTemplate = new TestRestTemplate(user.name, user.password)
            RequestEntity request = RequestEntity.get(serviceURI(endpoint)).build()
        when:
            ResponseEntity response = restTemplate.exchange(request, Object)
        then:
            response.statusCode == status
        where:
            endpoint                  | user                 || status
            ""                        | ROLE_TO_USER.NO_ROLE || HttpStatus.UNAUTHORIZED
            ""                        | ROLE_TO_USER.USER    || HttpStatus.OK
            ""                        | ROLE_TO_USER.ADMIN   || HttpStatus.OK
            "search/byFirstName/John" | ROLE_TO_USER.NO_ROLE || HttpStatus.UNAUTHORIZED
            "search/byFirstName/John" | ROLE_TO_USER.USER    || HttpStatus.OK
            "search/byFirstName/John" | ROLE_TO_USER.ADMIN   || HttpStatus.OK }
}
```

## Integration test configuration
As mentioned earlier, the dedicated Spring Boot profile *integration* is used for integration testing. This allows us to provide specific configuration parameters in file `application-integration.yml`. We use the file to define the port that application is running on during tests and *MongoDB* connection parameters.

```yaml
server.port: 9000

spring.data.mongodb:
  host: 192.168.59.103
  port: 29017
```


And finally everything is ready. Execute `gradle integrationTest` to start database, run the application, execute integration test and then stop application and database.

## Summary
We've seen how to add integration test phase to *Gradle* and how to start and stop *Docker* containers from build script.

*Spock* allows to write integration test that are easy to implement and understand. The integration with Spring Boot Testing works flawlessly.

This has been my first contact with *Spock* and I'm very sure that I'll continue using it instead of *jUnit* in the future. And when it comes to *Docker*, I'm like any other developer: lovin' it. Managing *Docker* containers from Gradle will for sure be helpful in other projects as well.

Do you have any ideas on how to improve the solution, like simplifying integration test setup in build script or implementation of *Spock* tests? Then please let me know in the comments.