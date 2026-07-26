package com.homecontrol.ios.cameras.onvif

/**
 * Just enough XML text/attribute extraction for the small, known-shape ONVIF
 * SOAP responses this app cares about -- not a real XML parser. `NSXMLParser`
 * (the Foundation option) is delegate/callback-based and considerably more
 * code for what's ultimately "pull the text out of one element" a handful of
 * times; a real parser correctly handles nesting, namespaces, CDATA, escaped
 * entities and more that these regexes don't, but ONVIF responses from real
 * camera firmware are simple, flat, and don't nest an element inside another
 * of the same local name, which is the case these regexes would actually get
 * wrong. Matches the file's existing pattern of "pragmatic, tested against
 * real hardware" over textbook-complete.
 */
internal object SimpleXml {

    /** First `<...localName...>text</...localName...>` element's text content, tag prefix (namespace alias) agnostic. */
    fun firstElementText(xml: String, localName: String): String? {
        val regex = Regex("<(?:[\\w.-]+:)?$localName\\b[^>]*>(.*?)</(?:[\\w.-]+:)?$localName>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** The value of [attributeName] on the first `<...localName ... attributeName="value" .../>` element found. */
    fun firstElementAttribute(xml: String, localName: String, attributeName: String): String? {
        val tagRegex = Regex("<(?:[\\w.-]+:)?$localName\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
        val tagBody = tagRegex.find(xml)?.groupValues?.get(1) ?: return null
        val attrRegex = Regex("$attributeName\\s*=\\s*\"([^\"]*)\"")
        return attrRegex.find(tagBody)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }
}
