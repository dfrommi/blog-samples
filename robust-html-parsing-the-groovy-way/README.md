---
posted: 2010-02-11
tags: [groovy, script, web]
---

# Robust HTML parsing the Groovy way

With Groovy, it's very easy to parse XML data and extract arbitrary
information. This works great as long as the input data is well-formed,
but you can't always guarantee that in real-world scenarios. Think of
extracting data from HTML pages. They are very often a mess when it
comes to XML validity and that's where the [TagSoup library](http://home.ccil.org/~cowan/XML/tagsoup/ "TagSoup") comes to the rescue.

There are two major problems with HTML input:

- DTD resolution
- Missing closing tags

We are going to build a simple Groovy script that prints the list of
questions on StackOverflow's start page. The straight forward solution
looks something like that

```groovy
def slurper = new XmlSlurper()
def htmlParser = slurper.parse("http://stackoverflow.com/")

htmlParser.'**'.findAll{ it.@class == 'question-hyperlink'}.each {
    println it
}
```

We parse <http://stackoverflow.com> with XMLSlurper, loop over all tags
with the class attribute 'question-hyperlink' and print it. But when
running the script we get the following exception:

> Caught: java.io.IOException: Server returned HTTP response code: 503
> for URL: http://www.w3.org/TR/html4/strict.dtd at
> html\_parser.run(html\_parser.groovy:7)

XMLSlurper has problems with HTML DTDs. By using the information in
[this post](http://stevefinck.blogspot.com/2009/12/groovy-xmlslurper.html "Groovy XmlSlurper and HTTP 503 Response Code"), we get rid of the exception.

```groovy
def slurper = new XmlSlurper()
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
def htmlParser = slurper.parse("http://stackoverflow.com/")

htmlParser.'**'.findAll{ it.@class == 'question-hyperlink'}.each {
    println it
}
```

So next try. The DTD exception is gone, but we get another one saying
the closing link-tag is missing. And here comes TagSoup. It's a library
that tries to transform invalid HTML data into well-formed XML. And best
of all, it works great together with XMLSlurper. Here is the final
Script:

```groovy
@Grab(group='org.ccil.cowan.tagsoup',
      module='tagsoup', version='1.2' )
def tagsoupParser = new org.ccil.cowan.tagsoup.Parser()
def slurper = new XmlSlurper(tagsoupParser)
def htmlParser = slurper.parse("http://stackoverflow.com/")

htmlParser.'**'.findAll{ it.@class == 'question-hyperlink'}.each {
    println it
}
```

The first command uses the @Grab-annotation to load the TagSoup library.
Next we create a TagSoup-Parser instance and pass it as
constructor-parameter to XMLSlurper. That's all and we even got rid of
the *setFeature* workaround.

You know other tricks to make HTML parsing more robust? Then please
leave them in the comments.