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