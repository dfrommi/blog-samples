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

	if(elem in Image && !(elem.url.contains("://") || elem.url.startsWith("/"))) {
		elem.url = "${meta.metadata.topic_url_raw}/${elem.url}"
	}
	
	if(elem in Link && !(elem.url.contains("://") || elem.url.startsWith("/"))) {
		elem.url = "${meta.metadata.topic_url}/${elem.url}"
	}
		
	elem
}

def toText(Inline[] inlines) {
	inlines.inject("") { text, item -> text + ((item instanceof Space) ? " " : item.text) }
}
