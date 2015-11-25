---
posted: 2014-02-09
tags: [camel, gradle, spring, spring boot]
---

# Spring Boot meets Apache Camel

[Apache Camel](http://camel.apache.org/) is a integration
framework and [Spring Boot](http://projects.spring.io/spring-boot/) is a
project trying to simplify configuration of Spring applications as much
as possible. I thought, the combination of both would be very nice and
assembled a tiny test project.

It should not only be possible to use
Camel routes in application core, but also servlet endpoints should be
accessible, i.e. direct access to Camel routes from web, while Spring
Boot endpoints like REST services are still available.

To set up the link between both frameworks, 2 things are required:

- Export `CamelContext` as Spring Bean
- Configure `CamelHttpTransportServlet`

Gradle is my build tool of choice, so let's start with build script.

```groovy
buildscript {
    ext {
        springBootVersion = '1.0.0.RC1'
    }
    repositories {
        mavenLocal()
        maven { url "http://repo.spring.io/libs-release" }
    }
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:${springBootVersion}")
    }
}

apply plugin: 'java'
apply plugin: 'eclipse'
apply plugin: 'spring-boot'

targetCompatibility = 1.6
sourceCompatibility = 1.6

jar {
    baseName = 'spring-boot-camel'
    version =  '0.0.1-SNAPSHOT'
}

repositories {
    mavenCentral()
    maven { url "http://repo.spring.io/libs-release" }
}

dependencies {
    compile("org.springframework.boot:spring-boot-starter-web:${springBootVersion}")
    compile("org.springframework.boot:spring-boot-starter-actuator:${springBootVersion}")

    compile('org.apache.camel:camel-servlet:2.12.2')
    compile('org.apache.camel:camel-spring-javaconfig:2.12.2')

    testCompile("org.springframework.boot:spring-boot-starter-test:${springBootVersion}")
}

task wrapper(type: Wrapper) {
    gradleVersion = '1.8'
}
```

It's mostly standard, only configuration of spring-boot Gradle plugin
requires some work, because it's not available in default repositories.
Apart from that, a few dependencies are configured, but that's it
already.

Once everything is in place, Spring Boot's embedded tomcat is
started with `gradle runBoot`.

Spring is configured using a Java
Config class. Thanks to Spring Boot, there's hardly anything required to
get the Spring application up and running. Just a main method and a few
annotations do the trick.

The link between Spring Boot and Apache Camel
is established by exposing `CamelContext` as Spring Bean and registering
a Camel servlet. That's why the Spring config class has a few additional
methods. Let's have a look at `Application.java`.

```java
package mr.f.spring_boot_camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
@ComponentScan
public class Application {
    private static final String CAMEL_URL_MAPPING = "/camel/*";
    private static final String CAMEL_SERVLET_NAME = "CamelServlet";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
     }

    @Bean
    public ServletRegistrationBean servletRegistrationBean() {
        ServletRegistrationBean registration = new ServletRegistrationBean(new CamelHttpTransportServlet(), CAMEL_URL_MAPPING);
        registration.setName(CAMEL_SERVLET_NAME);
        return registration;
    }

    @Bean
    public SpringCamelContext camelContext(ApplicationContext applicationContext) throws Exception {
        SpringCamelContext camelContext = new SpringCamelContext(applicationContext);
        camelContext.addRoutes(routeBuilder());
        return camelContext;
    }

    @Bean
    public RouteBuilder routeBuilder() {
        return new MyRouteBuilder();
    }
}
```

Most of it should be self-explanatory, but there are a few important
details.

It's very important to expose `CamelContext` as
`SpringCamelContext`. If we would just use a plain
`DefaultCamelContext`, the Camel servlet would not detect the context
automatically and have no routes.

A `ServletRegistrationBean` is used to
register additional servlets with Spring Boot. The URL mapping "`/*`" is
used by default, but that hides access to Spring Boot servlets and is
certainly not what we want. That's why we're using "`/camel/*`".

It's also very important to set the name of `CamelHttpTransportServlet` to
"CamelServlet", otherwise one has to provide the servlet name as
parameter for each servlet endpoint in Camel. Took me forever to find
this out.

That's it already. Just define some routes and you can access
Camel endpoints at http://localhost:8080/camel/

For testing puposes, 2 additional classes should be set up. First one is
Camel's `RouteBuilder` implementation, other one is a test REST
controller.

```java
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
```

This is a minimal RouteBuilder implementation, configuring one Servlet
endpoint, which is available at http://localhost:8080/camel/hello
and a timer endpoint to verify that routes are startet before first
servlet access. You can also autowire any other Spring resource using
`@Autowired` annotation.

To ensure that Camel servlet is not hiding
Spring Boot REST services, the following class implements a Hello World
Rest Service, accessible at http://localhost:8080/rs/hello.

```java
package mr.f.spring_boot_camel;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @RequestMapping(value = "/rs/hello")
    public String index() {
        return "Hello from REST!";
    }
}
```

That's all. With almost no effort, Apache Camel and Spring Boot play together just fine. 
The example project can be run with `gradle bootRun`.

*Update 2015/06/20:* Changed `runBoot` to `bootRun` and made classes public. (thanks to Nicolas Grange for pointing that out)