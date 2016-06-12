---
posted: 2016-06-09
tags: [pandoc, groovy, markdown, github]
comments: 13
---

# Pandoc with Github Syntax Highlighting

*Pandoc* - the swiss army knife of document conversion - supports syntax highlighting out of the box. But I prefer Github's way of syntax highlighting, and even that can be included in *Pandoc*.

We use a filter to replace code blocks by corresponding html code with highlighting. Github offers an [API](https://developer.github.com/v3/markdown/) to render markdown as html - including syntax highlighting - and we'll use it to get the html from raw code.

You can write *Pandoc* filters in many different languages, but I prefer *Groovy*. It's my favorite programming language and I'm the developer of the *Grovvy-Pandoc* wrapper. But the following example will work in any language. At the end of the day, it's only *JSON*. Now enough blah blah, here's the [filter](GithubHighlight.groovy).

```groovy
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
````

Biggest part is calling the API. Turned out to be a little bit tricky, because the `RESTClient` tries to be smart and parses the response by default, `html` in this case. By setting several `header` properties, it goes back to dumb mode.

Github's API has some rate limits and they are quite restrictive if the call is made unauthorized. That's why you can also provide an API token via environment variable `GITHUB_API_TOKEN`. Rate limits are then less restrictive, which is important if you try to convert many documents or have many distinct code blocks.

The generated code only contains *html* with classes assigned to elements. To make it colorful, a css stylesheet is required. The [highlight.css](highlight.css) file contains Github's syntax highlighting definition at the time of writing.

And finally, to create the *html* file from markdown, use this command

```bash
pandoc -s --no-highlight --css highlight.css README.md  --filter ./GithubHighlight.groovy -o README.html
```

The filter shown in this article should give you a good idea on how to use an API for syntax highlighting. If you know similar services that are worth taking a look at, please let me know in the comments.
