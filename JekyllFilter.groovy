#!/usr/bin/env groovyclient

@GrabResolver('https://jitpack.io')
@Grab('com.github.dfrommi:groovy-pandoc:v0.2.2')

import static net.frommknecht.pandoc.Pandoc.*
import net.frommknecht.pandoc.types.*

toJSONFilter { def elem, def meta ->
    if(elem in Header && elem.level == 1) {
		meta.metadata['title'] = toText(elem.text)
		return []
	}
	
	if((elem in Image || elem in Link) && !elem.url.startsWith("http")) {
		new File("meta.tmp") << meta.metadata
		elem.url = "${meta.metadata.topic_url}/${elem.url}"
	}
	
	if(elem in CodeBlock) {
		elem.attr.identifier="someId"
		elem.attr.classes << "cl1" << "cl2"
		elem.attr.properties['p1'] = 'v1'
	}
	
	elem
}

def toText(Inline[] inlines) {
	inlines.inject("") { text, item -> text + ((item instanceof Space) ? " " : item.text) }
}
