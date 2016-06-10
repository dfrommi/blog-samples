#!/usr/bin/env groovy

@GrabResolver('https://jitpack.io')
@Grab('com.github.dfrommi:groovy-pandoc:v0.6')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')

import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

import static com.github.dfrommi.pandoc.Pandoc.*
import com.github.dfrommi.pandoc.types.*

toJSONFilter(CodeBlock) { 
  highlight(it)
}

def highlight(CodeBlock cb) {
  def githubToken = System.getenv("GITHUB_API_TOKEN")
    
  def client = new RESTClient("https://api.github.com")
  client.contentType = ContentType.TEXT
  client.headers = [Accept : 'text/html', 'User-Agent': "pandoc/1.17.0.3"]
  if(githubToken) {
    client.headers << [Authorization: "token ${githubToken}"]
  }
  
  String language = cb.attr.classes ? cb.attr.classes.first() : ''
  String content = "```${language}\n${cb.code}\n```"
  def resp = client.post(path: "/markdown", requestContentType: ContentType.JSON, body: [text: content])
  return new RawBlock(format: "html", content: resp.data.text)
}