package com.github.dfrommi.wiremock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.RequestPatternBuilder
import com.github.tomakehurst.wiremock.junit.WireMockRule
import groovy.json.JsonOutput
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.Rule
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo

class WireMockExtensionSpec extends Specification {
    static final MappingBuilder API_GET_ENDPOINT = get(urlEqualTo("/api/resource"))
    static final URL API_ENDPOINT_URL = "http://localhost:8080/api/resource".toURL()
    static final RequestPatternBuilder API_GET_REQUEST = getRequestedFor(urlEqualTo("/api/resource"))
    static final RequestPatternBuilder API_POST_REQUEST = postRequestedFor(urlEqualTo("/api/resource"))

    @Rule
    WireMockRule server = new WireMockRule()

    def "classic java notation"() {
        given:
        server.givenThat(API_GET_ENDPOINT.willReturn(aResponse().withStatus(200)))

        when:
        apiCalled()

        then:
        server.verify API_GET_REQUEST
    }

    def "stub using leftshift operator"() {
        given:
        server << API_GET_ENDPOINT.willReturn(aResponse().withStatus(200))

        when:
        apiCalled()

        then:
        server.verify API_GET_REQUEST
    }

    def "stub using assignment operator and map notation"() {
        given:
        server[API_GET_ENDPOINT] = aResponse().withStatus(200)

        when:
        apiCalled()

        then:
        server[API_GET_REQUEST].size() == 1
    }

    def "stub using assignment operator, map notation and status shortcut"() {
        given:
        server[API_GET_ENDPOINT] = 200

        when:
        apiCalled()

        then:
        server[API_GET_REQUEST].size() == 1
    }

    def "parsed json body from post request"() {
        given:
        def jsonData = [aKey: 'aValue', anotherKey: 'anotherValue']

        when:
        performPostRequest(jsonData)

        then:
        with(server[API_POST_REQUEST].jsonBody) {
            aKey == 'aValue'
            anotherKey == 'anotherValue'
        }
    }

    def apiCalled() {
        API_ENDPOINT_URL.text
    }

    def performPostRequest(jsonData) {
        HttpClient client = HttpClientBuilder.create().build()
        HttpPost post = new HttpPost(API_ENDPOINT_URL.toURI())
        StringEntity stringEntity = new StringEntity(JsonOutput.toJson(jsonData))
        post.setEntity(stringEntity)
        post.setHeader("Content-type", ContentType.APPLICATION_JSON.mimeType)

        client.execute(post)
    }
}
