package mr.f

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.boot.test.TestRestTemplate
import org.springframework.boot.test.WebIntegrationTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Stepwise

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
