package mr.f

import mr.f.domain.Person
import org.springframework.boot.test.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Unroll

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
