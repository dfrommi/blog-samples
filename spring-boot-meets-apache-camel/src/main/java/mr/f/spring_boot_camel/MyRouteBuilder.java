package mr.f.spring_boot_camel;

import org.apache.camel.builder.RouteBuilder;

public class MyRouteBuilder extends RouteBuilder {
	@Override
	public void configure() throws Exception {
		// Access us using http://localhost:8080/camel/hello
		from("servlet:///hello").transform().constant("Hello from Camel!");
		
		// Trigger run right after startup. No Servlet request required.
		from("timer://foo?fixedRate=true&period=10s").log("Camel timer triggered.");
	}
}
