---
posted: 2016-06-26
tags: [wiremock, groovy, spock]
comments: 14
---

# WireMock with Groovier Syntax

[WireMock](http://wiremock.org) is a library for stubbing and mocking web services. It makes heavy use of the builder pattern and has a very expressive syntax.
But with Groovy's operator overloading, it can be improved even further.

Groovy's extension modules allow adding new methods to existing classes without touching the original source code. And that even works with operator overloading.
This article describes how to use this toolset to simplify *WireMock's* syntax even further.

If you are not familiar with extension modules, find a short introduction [here](http://mrhaki.blogspot.de/2013/01/groovy-goodness-adding-extra-methods.html).
Operator overloading is described [here](http://groovy-lang.org/operators.html#Operator-Overloading).

## Preparation
The extension module is implemented in class `WireMockExtensions`. To inform Groovy runtime about it, file `META-INF/services/org.codehaus.groovy.runtime.ExtensionModule` with following content is required

```
moduleName = wiremock-module
moduleVersion = 0.1
extensionClasses = com.github.dfrommi.wiremock.WireMockExtensions
```

The *WireMock* extension methods will be presented together with a *Spock* test that is using it. First an example using default syntax and some variable definitions that will be used throughout this article

```groovy
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
```

An API endpoint is defined that returns http code `200` with empty response. A helper method calls the API and it is verified that the endpoint was called at least once.

## Left shift operator

Instead of using `givenThat` or `stubFor`, Groovy's left shift operator `<<` can be used with this extension method

```groovy
static WireMockRule leftShift(WireMockRule self, MappingBuilder mappingBuilder) {
  self.givenThat(mappingBuilder)
  self
}
```

`WireMockRule` is returned to support chaining. The `given` section of the test then looks like this

```groovy
given:
server << API_GET_ENDPOINT.willReturn(aResponse().withStatus(200))
```

## Map access syntax

We can also see `WireMockRule` as a map of endpoints where getting values returns all invocations with that endpoint
and assigning values configures the endpoint's response.

```groovy
static List<LoggedRequest> getAt(WireMockRule self, RequestPatternBuilder requestPatternBuilder) {
  self.findAll(requestPatternBuilder)
}

static void putAt(WireMockRule self, MappingBuilder mappingBuilder, ResponseDefinitionBuilder responseBuilder) {
  self.stubFor(mappingBuilder.willReturn(responseBuilder))
}
```

Test preparation and verification can be re-written like that

```groovy
def "stub using assignment operator and map notation"() {
    given:
    server[API_GET_ENDPOINT] = aResponse().withStatus(200)

    when:
    apiCalled()

    then:
    server[API_GET_REQUEST].size() == 1
}
```

If return code with empty response is a common use case, we can also add a shortcut for that and only expect an `integer` in the assignment

```groovy
static void putAt(WireMockRule self, MappingBuilder mappingBuilder, int status) {
  self.stubFor(mappingBuilder.willReturn(aResponse().withStatus(status)))
}
```

Which leads to a test setup of

```groovy
given:
server[API_GET_ENDPOINT] = 200
```

Depending on the most common use cases in the application under test, more simplifications can be set up. For example, assignment of a map
could lead to a response with status code `200` and serialised JSON response body.

## JSON body
*WireMock* has some support for JSON matching, but there is no built in way to get the parsed JSON body from a request. Following two extension methods add this functionality.
The first one returns the JSON body of an individual request, the second one is a convenience method to get it from the first of several responses.

```groovy
static def getJsonBody(LoggedRequest self) {
  new JsonSlurper().parse(self.body)
}

static def getJsonBody(List<LoggedRequest> self) {
  self[0].jsonBody
}
```

And this test is using `jsonBody` in combination with previously implemented map access.

```groovy
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
```

The call to `server[API_POST_REQUEST].jsonBody` returns a `Map` in our case and *Spock's* `with` method make verification of its entries very easy.

## Summary
In this article, we've seen how to simplify *WireMock* syntax using extension modules and operator overloading.
It only scratches on the surface of what is possible. More operators and methods can be added to define your application
specific DSL.
If you have more ideas on how to extend the syntax of *WireMock*, then please leave a comment.

The full source is as always available [on Github](.).
