---
posted: 2015-10-31
tags: [spock, testing]
---

# Spock: share common test setup with Traits
	
Common test data can be shared either by putting it into helper classes or in a common test base class. But there's a third way. *Spock* has full support for `Traits` and this post will explain how to use it.

`Traits` are pretty much like classes, but used like interfaces. Non-private properties and methods are available in the class that implements the `Trait`. And this can be used in *Spock* specifications.

Let's quickly set up a test project, starting with a minimal *gradle* built script that supports *Spock*.

```groovy
version '1.0'

apply plugin: 'groovy'

repositories {
    mavenCentral()
}

dependencies {
    compile "org.codehaus.groovy:groovy-all:2.4.1"
    testCompile "org.spockframework:spock-core:1.0-groovy-2.4"
}
```

And now a `Service` to test

```groovy
package mr.f

class Service {
    String greet(User user) {
        "Hello ${user.firstname} ${user.lastname}!"
    }
}
```

The `greeting` method requires a `User` domain object and creates a greeting String from it

```groovy
package mr.f

class User {
    String firstname
    String lastname
}
```

Now that everything is in place, we can test the `greeting` method. 

Let's assume that `User` domain objects are widely used in the application and test users are frequently required in unit tests. So we want an easy way to share test user data. A `Trait` can be used, containing a `user` property that is initialized in the `Trait`'s `setup` method.

```groovy
package mr.f

import org.junit.After
import org.junit.Before

trait UserSpecTrait {
    User user

    @Before
    def setupUserSpec() {
        println "Calling setup in Trait..."
        user = new User(firstname: "Arthur", lastname: "Dent")
    }

    @After
    def cleanupUserSpec() {
        println "Calling cleanup in Trait..."
    }
}
```

There's an important catch. Usually method names `setup` and `cleanup` are expected by *Spock* to perform test preparation and cleanup. But if the `Trait` would use those method names, they could no longer be used in the `Specification` class. That's why they have to have different names in the `Trait`, like in this case `setupUserSpec` and `cleanupUserSpec`. To inform *Spock* about their purpose, they need to be annotated with  *jUnit's* annotations `@Before` and `@After`.

And finally the *Spock* specification

```groovy
package mr.f

import spock.lang.Specification

class ServiceSpec extends Specification implements  UserSpecTrait {
    Service service
    User anotherUser

    def setup() {
        println "Calling setup in Spec..."
        service = new Service()
        anotherUser = new User(firstname: 'Ford', lastname:'Prefect')
    }

    def cleanup() {
        println "Calling cleanup in Spec"
    }

    def "greeting Arthur"() {
        when:
        def greeting = service.greet(user)

        then:
        greeting == "Hello Arthur Dent!"
    }

    def "greeting Ford"() {
        when:
        def greeting = service.greet(anotherUser)

        then:
        greeting == "Hello Ford Prefect!"
    }
}
``` 

Test method `greeting Arthur` uses the Trait's `user` object. On top of that, the Spec defines its own user test object which is used in `greeting Ford` method.

When you run the test in gradle using `gradle clean test -i`, the following output is printed

```
mr.f.ServiceSpec > greeting Arthur STANDARD_OUT
    Calling setup in Trait...
    Calling setup in Spec...
    Calling cleanup in Spec
    Calling cleanup in Trait...

mr.f.ServiceSpec > greeting Ford STANDARD_OUT
    Calling setup in Trait...
    Calling setup in Spec...
    Calling cleanup in Spec
    Calling cleanup in Trait...
```

As you can see, the `Trait`'s setup is called first, then the one from `Spec`. And after test execution, first the `Spec` cleanup is done and then the one from `Trait`. That's exactly the sequence I would expect.

I like this way of sharing test setup a lot and will for sure use that quite frequently.
