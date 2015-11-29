---
posted: 2013-10-05
tags: [camel, groovy, script, web]
---

# Web-crawling using Apache Camel and jQuery selectors

This article explains how to do web-crawling with Apache Camel's Groovy
DSL and jQuery-like selectors from jSoup library.

Using an example script that grabs all images from [Macenstein's Mac Chick Of The
Month](http://macenstein.com/default/archives/mac-chick-of-the-month/)
page, I'll walk you through the different parts required for simple
crawling. This task is perfectly suited to see the power of this
approach, because images are contained in a variety of ways making the
test script much more realistic than a plain Hello World. The beautiful
content is just a very pleasant side effect.

The script follows links to
blog posts and pagination ("Next page"), extracts image references from
HTML pages, saves images or follows links to them until the final image
was reached. Processing aborts if either all images were saved or the
image already exists on harddisk.

Apache Camel turns out to be perfect
for defining the workflow, i.e. following links, saving images and
converting URLs to HTML content.

With jSoup on the other hand it's easy
to extract data from HTML pages by using its jQuery-like selector
syntax.

At the end of the day, the hardest part is to define the list of
image selectors. The way images are included in Macenstein's MCOTM
archive changed significantly over last 7 years making it a challenging
task.

Now let's start and have a look at the full groovy script.

```groovy
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.jsoup.Jsoup
import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.model.language.SimpleExpression
import org.apache.camel.component.file.GenericFileOperationFailedException

@Grapes([
    @Grab("org.apache.camel:camel-groovy:2.11.1"),
    @Grab("org.jsoup:jsoup:1.7.2"),
    @Grab("org.slf4j:slf4j-simple:1.7.5")])
class McotmGrabber extends RouteBuilder {
    // Target directory to store files
    def TARGET_DIR = "/Users/dennis/Inbox/mcotm"

    // List of jSoup selectors for links to follow and images to download
    def LINK_SELECTOR = [
        // image tags inside of external links
        'div.postarea a[href~=^(?!http://(www.)?macenstein.com)] img[src~=http.*macenstein]',
        // links to image pages
        'div.postarea a[href~=http.*macenstein]:has(img[src~=http.*macenstein])',
        // image tags not inside of links
        'div.postarea *:not(a) > img[src~=http.*macenstein]',
        // don't follow dead link but use image instead
        'div.postarea a[href^=http://macenstein.com/default/archives] > img[src~=http.*macenstein]',
        // pagination within post
        'div.postarea a[href~=^http.*macenstein.*/\\d/?$]'
    ].join(',')

    // Picture to ignore, like ads and banners
    def BLACKLIST = [
        'http://www.macenstein.com/forum/',
        '^http://macenstein.com/default/archives/\\d+(/\\d*)?$',  //dead links
        'http://www.macenstein.com/images/mg/mg_end_bar.jpg',
        'http://www.macenstein.com/images/mg/macchick2.jpg',
        'http://macenstein.com/images/banners/mcotm_contest_banner.jpg',
        'http://www.macenstein.com/banners/ads/macheist/Bigbox02.png',
        'http://www.macenstein.com/images/mg/2007_04/minx.jpg',
        'http://www.macenstein.com/images/2009/mg/09_10/boom.jpg'
    ]

    // Track visited link to avoid loops
    def visitedLinks = []

    // Workflow runs only as long as this is true
    def workflowEnabled = true

    static void main(String[] args) {
        CamelContext context = new DefaultCamelContext()
        // Streamcaching required if stream is used more than once
        context.setStreamCaching(true)
        context.addRoutes(new McotmGrabber())

        // Producer template used to only have single execution of route
        ProducerTemplate template = context.createProducerTemplate()
        context.start()
        // Send landing page to route and start the workflow
        template.sendBody("direct:postList", new URL("http://macenstein.com/default/archives/mac-chick-of-the-month/"))
        // Testing of  individual posts
        //template.sendBody("direct:findLinks", new URL("http://macenstein.com/default/2012/02/" +
        //  "macensteins-mac-chick-of-the-month-february-2012-mandie-mutchie/"))
    }

    /**
     * Creates closure to select elements from exchange body using jSoup selectors.
     */
    Closure jsoup(String selector) {
        return { Exchange exchange ->
            def document = Jsoup.parse(exchange.in.getBody(String.class))
            document.select(selector)
        }
    }

    @Override
    void configure() throws Exception {
        // Closure returning URL that an element (a or img) is pointing to
        def toTargetUrl = {
            it.in.body.with {
                new URL(attr(tagName() == "img" ? "src" : "href"))
            }
        }

        // Closure to test if URL is pointing to an image
        def isImage = { Exchange e ->
            e.in.getBody(URL).path ==~ /.*\.(png|jpe?g)$/
        }

        // Closure that decides if workflow should follow that link by checking blacklist and already visited links
        def shouldFollowLink = { Exchange e ->
            URL url = e.in.getBody(URL)
            def isBlacklisted = BLACKLIST.any {
                url.toExternalForm().find(it) != null
            }
            !isBlacklisted && !visitedLinks.contains(url)
        }

        // Closure to mark a link as visited
        def markAsVisited = { Exchange e ->
            visitedLinks << e.in.getBody(URL)
        }

        // Shortcut to follow a list of links
        ProcessorDefinition.metaClass.forAllLinks = { String expression ->
            delegate.split(jsoup(expression)).transform(toTargetUrl)
        }

        // Shortcut to test if workflow is still enabled
        ProcessorDefinition.metaClass.checkWorkflowEnabled = {
            delegate.filter({workflowEnabled})
        }

        // Highlight dead links
        onException(FileNotFoundException)
            .handled(true)
            .log(LoggingLevel.ERROR, 'DEAD LINK: ${exception.message}')

        // Entry point
        from("direct:postList").routeId("postList")
            .multicast().to("direct:findAllPosts", "direct:findNextPage")

        // Follows pagination links
        from("direct:findNextPage").routeId("findNextPage")
            .checkWorkflowEnabled()
            .forAllLinks("#nextLink a")
            .log('Going to next page: ${body.toExternalForm}')
            .to("direct:postList")

        // Searches for links to posts
        from("direct:findAllPosts").routeId("findAllPosts")
            .split(jsoup(".postarea h3 > a"))
            .checkWorkflowEnabled()
            .log('==== ${body.text()} ====')
            .transform(toTargetUrl)
            .to("direct:findLinks")

        // Searches for links inside of posts using LINK_SELECTOR list.
        // It either recursively follows links or calls route to save image
        from("direct:findLinks").routeId("findLinks")
            .checkWorkflowEnabled()
            .forAllLinks(LINK_SELECTOR)
            .filter(shouldFollowLink)
            .process(markAsVisited)
            .choice()
                .when(isImage).to("direct:saveImage")
                .otherwise().to("direct:findLinks")

        // Saves image, aborting workflow if it already exists
        from("direct:saveImage").routeId("saveImage")
            .checkWorkflowEnabled()
            // set filename from URL
            .setHeader(Exchange.FILE_NAME) {new File(it.in.body.file).name}
            .log('Saving image: ${body.toExternalForm} -> ${header.CamelFileName}')
            .choice()
                // abort if file already exists
                .when({new File(TARGET_DIR, it.in.getHeader(Exchange.FILE_NAME)).exists()})
                    .process({workflowEnabled = false})
                    .log('File already exists. Workflow stopped')
                .otherwise()
                    .to("file:${TARGET_DIR}")
    }
}
```

As you can see, the script is neither very long nor overly complex.
Let's still have a closer look.

```groovy
@Grapes([
    @Grab("org.apache.camel:camel-groovy:2.11.1"),
    @Grab("org.jsoup:jsoup:1.7.2"),
    @Grab("org.slf4j:slf4j-simple:1.7.5")])
```
Grapes downloads
dependencies and adds them in Classpath. Camel's Groovy DSL, jSoup and
Slf4j are required.

```groovy
class McotmGrabber extends RouteBuilder {
```
The main class extends Camel's
`RouteBuilder`. Then it's not required to use an anonymous class to
configure routes.

```groovy
def LINK_SELECTOR = [
    'div.postarea a[href~=^(?!http://(www.)?macenstein.com)] img[src~=http.*macenstein]',
    'div.postarea a[href~=http.*macenstein]:has(img[src~=http.*macenstein])',
    'div.postarea *:not(a) > img[src~=http.*macenstein]',
    'div.postarea a[href^=http://macenstein.com/default/archives] > img[src~=http.*macenstein]',
    'div.postarea a[href~=^http.*macenstein.*/\\d/?$]'
].join(',')
```
The `LINK_SELECTOR` list defines all jSoup selectors used to extract images
and links from Macenstein's archive. This is the tedious part. I created
it by opening the page in Chrome, inspecting image elements and tried to
come up with a selector for it.

```groovy
def BLACKLIST = [
    'http://www.macenstein.com/forum/',
    '^http://macenstein.com/default/archives/\\d+(/\\d*)?$',  //dead links
    'http://www.macenstein.com/images/mg/mg_end_bar.jpg',
    'http://www.macenstein.com/images/mg/macchick2.jpg',
    'http://macenstein.com/images/banners/mcotm_contest_banner.jpg',
    'http://www.macenstein.com/banners/ads/macheist/Bigbox02.png',
    'http://www.macenstein.com/images/mg/2007_04/minx.jpg',
    'http://www.macenstein.com/images/2009/mg/09_10/boom.jpg'
]
```
Selectors in `LINK_SELECTOR` list
are matching too many elements. Ads, banners and known dead links should
be excluded. Whatever is contained in `BLACKLIST` is ignored by the
workflow, which means links are not followed and images are not
downloaded. It might be possible
to incorporate the blacklist in selectors, but it makes them very hard
to understand.

```groovy
def visitedLinks = []
```
We keep track of already visited links to
avoid loops and duplicate downloads.

```groovy
def workflowEnabled = true
```
Workflow is stopped by setting
`workflowEnabled` to false. It's used to abort execution when an image
already exists on local disk.

```groovy
static void main(String[] args) {
    CamelContext context = new DefaultCamelContext()
    // Streamcaching required if stream is used more than once
    context.setStreamCaching(true)
    context.addRoutes(new McotmGrabber())

    // Producer template used to only have single execution of route
    ProducerTemplate template = context.createProducerTemplate()
    context.start()
    // Send landing page to route and start the workflow
    template.sendBody("direct:postList", new URL("http://macenstein.com/default/archives/mac-chick-of-the-month/"))
}
```
The `main` method sets up Apache Camel and starts the workflow.

If stream-caching is disabled (default), a stream can only be read once.
The same page is parsed multiple times to extract different kinds of
information and therefore stream caching needs to be enabled in our
case. Usually a camel route is started and remains open waiting
for incoming messages. But for our purpose only one message should be
sent to a route and after this single message was processed,  script
should abort. The `ProducerTemplate` does exactly this. The MCOTM
landing page is sent to route "`direct:postList`". If a specific post
should be examined, its URL can be sent directly to
"`direct:findLinks`". It's very helpful for debugging and setting up
selectors.

```groovy
Closure jsoup(String selector) {
    return { Exchange exchange ->
        def document = Jsoup.parse(exchange.in.getBody(String.class))
        document.select(selector)
    }
}
```
And here starts
the beauty of Camel's Groovy DSL. The method `jsoup` returns a Closure,
which evaluates an `Exchange` body using the given selector and returns
the list of matching elements. This is the link between Camel and
jSoup. Camel's Groovy DSL supports Closures in methods like `filter()`,
`process()` etc.

```groovy
void configure() throws Exception {
```
The main class extends `RouteBuilder`, therefore the `configure` method is
overridden to define routes. But it also defines several Closures that
are used within route definitions.

```groovy
def toTargetUrl = {
    it.in.body.with {
        new URL(attr(tagName() == "img" ? "src" : "href"))
    }
}
```
Closure `toTargetUrl` expects an
jSoup `Element` and converts it to target URL. For an anchor tag (`a`),
the `href` attribute is used and `src` attribute for an image element
(`img`).

```groovy
def isImage = { Exchange e ->
    e.in.getBody(URL).path ==~ /.*\.(png|jpe?g)$/
}
```
`isImage` is a Closure returning true if an URL is pointing to an image and false
otherwise.

```groovy
def shouldFollowLink = { Exchange e ->
    URL url = e.in.getBody(URL)
    def isBlacklisted = BLACKLIST.any {
        url.toExternalForm().find(it) != null
    }
    !isBlacklisted && !visitedLinks.contains(url)
}
```
Closure `shouldFollowLinks` checks if processing should continue for a URL, i.e.
it's neither blacklisted nor already visited.

```groovy
def markAsVisited = { Exchange e ->
    visitedLinks << e.in.getBody(URL)
}
```
`markAsVisited` adds the URL in`Exchange` body to list of visited links. 

```groovy
ProcessorDefinition.metaClass.forAllLinks = { String expression ->
    delegate.split(jsoup(expression)).transform(toTargetUrl)
}
```
It's not
only possible to use Closures in Groovy DSL, but with Groovy's metaclass
capabilities, new methods can be added to `ProcessorDefinition` class
making them available in route definition. `forAllLinks` is a shortcut
for extracting link elements from an HTML page using a jSoup selector,
converting them to target URL and executing the remaining route for
every single URL.

```groovy
ProcessorDefinition.metaClass.checkWorkflowEnabled = {
    delegate.filter({workflowEnabled})
}
```
`checkWorkflowEnabled` is another
shortcut, aborting a route if `workflowEnabled` it false. **[116-118]**
Here the real route definition starts. The global Exception handler on
`FileNotFoundException` is used to log dead links.

```groovy
from("direct:postList").routeId("postList")
    .multicast().to("direct:findAllPosts", "direct:findNextPage")
```
Route "`direct:postList`" is processing blog post overview pages like the
MCOTM landing page. It expects an URL as message body and forwards it to
routes "`direct:findAllPosts`" and "`direct:findNextPage`". This is the
entry point called by `main` method.

```groovy
from("direct:findNextPage").routeId("findNextPage")
    .checkWorkflowEnabled()
    .forAllLinks("#nextLink a")
    .log('Going to next page: ${body.toExternalForm}')
    .to("direct:postList")
```
Route `direct:findNextPage` is searching for pagination links, i.e. the "Next
Page" link using jSoup, which is hidden in the `forAllLinks` shortcut
defined earlier. For every link it finds, route `direct:postList` is
called to search for blog posts and next pagination link. This route is
responsible for the progress from one post list page to the next.

```groovy
from("direct:findAllPosts").routeId("findAllPosts")
    .split(jsoup(".postarea h3 > a"))
    .checkWorkflowEnabled()
    .log('==== ${body.text()} ====')
    .transform(toTargetUrl)
    .to("direct:findLinks")
```
`direct:findAllPosts` route searches for blog post links
on any blog list page. The route `findLinks` is called for every link it
finds.

```groovy
from("direct:findLinks").routeId("findLinks")
    .checkWorkflowEnabled()
    .forAllLinks(LINK_SELECTOR)
    .filter(shouldFollowLink)
    .process(markAsVisited)
    .choice()
        .when(isImage).to("direct:saveImage")
        .otherwise().to("direct:findLinks")
```
`direct:findLinks` processes blog posts searching
for images or links to them as well as blog post internal pagination
links. By using the globally defined `LINK_SELECTOR` list, elements are
extracted. If they are neither blacklisted nor already visited
(`shouldFollowLink` closure), they are marked as visited an either
followed recursively by calling this route with the new URL or if it's
an image, by calling `direct:saveImage` route to store it.

```groovy
from("direct:saveImage").routeId("saveImage")
    .checkWorkflowEnabled()
    // set filename from URL
    .setHeader(Exchange.FILE_NAME) {new File(it.in.body.file).name}
    .log('Saving image: ${body.toExternalForm} -> ${header.CamelFileName}')
    .choice()
        // abort if file already exists
        .when({new File(TARGET_DIR, it.in.getHeader(Exchange.FILE_NAME)).exists()})
            .process({workflowEnabled = false})
            .log('File already exists. Workflow stopped')
        .otherwise()
            .to("file:${TARGET_DIR}")
```
Last but not least `direct:saveImage` route is saving the
image. Exchange body contains the image's URL. First, the target
filename is derived from it by taking the filename part of the URL and
assign it to `FILE_NAME` header property. Afterwards it's checked if the
file already exists on harddisk and if so, the workflow is aborted by
setting `workflowEnabled` to false. Otherwise the file is saved.

Note that most routes contain a call to c`heckWorkflowEnabled()` which aborts
workflow if `workflowEnabled` property is set to false.

The script walkthrough is hereby finished. I hope you find the combination of
Apache Camel and jSoup as interesting as I do. If you have tips and
tricks on how to improve it even further, please let me know in the
comments.