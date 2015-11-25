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
		//template.sendBody("direct:findLinks", new URL("http://macenstein.com/default/2012/02/macensteins-mac-chick-of-the-month-february-2012-mandie-mutchie/"))
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



