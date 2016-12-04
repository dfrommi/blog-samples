#!/usr/bin/env groovy

@GrabResolver('https://jitpack.io')
@Grab('com.github.dfrommi:groovy-pandoc')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')

import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

import static com.github.dfrommi.pandoc.Pandoc.*
import com.github.dfrommi.pandoc.types.*

toJSONFilter { def elem, def meta ->
    if(elem in Header && elem.level == 1) {
		meta.metadata['title'] = toText(elem.text)
		return []
	}

	if(elem in Image && !(elem.url.contains("://") || elem.url.startsWith("/"))) {
		elem.url = "${meta.metadata.topic_url_raw}/${elem.url}"
	}

	if(elem in Link && !(elem.url.contains("://") || elem.url.startsWith("/"))) {
		elem.url = "${meta.metadata.topic_url}/${elem.url}"
	}

  if(elem in CodeBlock) {
    return highlight(elem)
  }

	elem
}

def toText(Inline[] inlines) {
	inlines.inject("") { text, item -> text + ((item instanceof Space) ? " " : item.text) }
}

def highlight(CodeBlock cb) {
  def githubToken = System.getenv("GITHUB_API_TOKEN")

  def client = new RESTClient("https://api.github.com")
  client.contentType = ContentType.TEXT
  client.headers = [Accept : 'text/html', 'User-Agent': "curl/7.43.0"]
  if(githubToken) {
    client.headers << [Authorization: "token ${githubToken}"]
  }

  String language = cb.attr.classes ? cb.attr.classes.first() : ''
  String content = "```${language}\n${cb.code}\n```"
  def resp = client.post(path: "/markdown", requestContentType: ContentType.JSON, body: [text: content])
  return new RawBlock(format: "html", content: "{% raw %}\n${resp.data.text}\n{% endraw %}")
}
