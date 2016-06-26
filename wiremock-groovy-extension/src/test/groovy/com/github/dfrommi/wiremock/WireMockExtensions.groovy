package com.github.dfrommi.wiremock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.RequestPatternBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import groovy.json.JsonSlurper
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse

class WireMockExtensions {
   static def getJsonBody(LoggedRequest self) {
      new JsonSlurper().parse(self.body)
   }

   static def getJsonBody(List<LoggedRequest> self) {
      self[0].jsonBody
   }

   static WireMockRule leftShift(WireMockRule self, MappingBuilder mappingBuilder) {
      self.givenThat(mappingBuilder)
      self
   }

   static List<LoggedRequest> getAt(WireMockRule self, RequestPatternBuilder requestPatternBuilder) {
      self.findAll(requestPatternBuilder)
   }

   static void putAt(WireMockRule self, MappingBuilder mappingBuilder, ResponseDefinitionBuilder responseBuilder) {
      self.stubFor(mappingBuilder.willReturn(responseBuilder))
   }

   static void putAt(WireMockRule self, MappingBuilder mappingBuilder, int status) {
      self.stubFor(mappingBuilder.willReturn(aResponse().withStatus(status)))
   }
}
