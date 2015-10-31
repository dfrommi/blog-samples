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
