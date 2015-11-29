---
posted: 2013-08-11
tags: [gradle, groovy, hibernate, hsqldb, logback, spring]
---

# Project template: Gradle, Groovy, Spring 3, Hibernate 4, HSQLDB and Logback

To have a simple quickstart in future private projects, I created a
small project template. With just 3 files, you get a Spring container
with Hibernate connected to an in memory database and slf4j/logback for
logging. Spring and Hibernate are configured to support annotations and
you can overwrite setting by providing an optional property file. Gradle
takes care of dependencies.

The following files are required:

- **build.gradle**: Gradle build script
- **src/main/resources/logback.groovy**: Logback minimal configuration
- **src/main/resources/applicationContext.xml**: Spring bean configuration

## build.gradle

The Gradle build script mainly configures dependencies and sets up slf4j
for logging by excluding commons-logging. The main class is also set.
When using the template, set it to your main class.

```groovy
apply plugin: 'groovy'
apply plugin: 'application'

mainClassName = 'df.ormtemplate.Main'

v = [
    groovy: '2.1.6',
    spring: '3.1.4.RELEASE',
    hibernate: '4.2.4.Final',
    junit: '4.11',
    slf4j: '1.7.5',
    logback: '1.0.13'
]

repositories {
    mavenCentral()
}

dependencies {

    compile (
        "org.codehaus.groovy:groovy-all:$v.groovy",
        "org.springframework:spring-context:$v.spring",
        "org.springframework:spring-core:$v.spring",
        "org.springframework:spring-orm:$v.spring",
        "org.springframework:spring-tx:$v.spring",
        "org.hibernate:hibernate-core:$v.hibernate",
        "org.hsqldb:hsqldb:2.3.0"
    )

    runtime (
        "commons-cli:commons-cli:1.2",
        "cglib:cglib:2.2.2"
    )

    // Logging
    compile "ch.qos.logback:logback-classic:$v.logback"
    runtime (
        "org.slf4j:slf4j-api:$v.slf4j",
        "org.slf4j:jcl-over-slf4j:$v.slf4j"
    )

    testCompile (
        "junit:junit:$v.junit"
    )
}

configurations {
    all*.exclude group: "commons-logging", module: "commons-logging"
}
```

##logback.groovy

Logback requires at least some basic configuration. This minimal setup
uses a ConsoleAppender, sending log messages to screen. Log level is set
to ERROR.

```groovy
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender

import static ch.qos.logback.classic.Level.ERROR

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    }
}
root(ERROR, ["STDOUT"])
```

## applicationContext.xml

Spring beans configuration is the heart of the template. It configures
the database, sets up Hibernate and annotation configuration.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:context="http://www.springframework.org/schema/context"
    xmlns:tx="http://www.springframework.org/schema/tx"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
     http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
     http://www.springframework.org/schema/context
     http://www.springframework.org/schema/context/spring-context-3.0.xsd
     http://www.springframework.org/schema/tx
     http://www.springframework.org/schema/tx/spring-tx-3.0.xsd">

    <!-- Config overrides -->
    <context:property-placeholder location="classpath:application.properties"
        ignore-resource-not-found="true" />

    <context:component-scan base-package="${packageToScan:df}" />
    <context:annotation-config />

    <!-- HSQLDB data source -->
    <bean id="dataSource"
        class="org.springframework.jdbc.datasource.DriverManagerDataSource">
        <property name="driverClassName"
            value="${jdbc.driverClassName:org.hsqldb.jdbcDriver}" />
        <property name="url" value="${jdbc.url:jdbc:hsqldb:mem:myAppDb}" />
        <property name="username" value="${jdbc.username:sa}" />
        <property name="password" value="$jdbc.password:}" />
    </bean>

    <!-- Hibernate session factory -->
    <bean id="sessionFactory"
        class="org.springframework.orm.hibernate4.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource" />
        <property name="packagesToScan" value="${packageToScan:df}" />
        <property name="hibernateProperties">
            <props>
                <prop key="hibernate.dialect">
                    ${hibernate.dialect:org.hibernate.dialect.HSQLDialect}
                </prop>
                <prop key="hibernate.hbm2ddl.auto">${hibernate.hbm2ddl.auto:update}</prop>
                <prop key="hibernate.show_sql">${hibernate.show_sql:false}</prop>
            </props>
        </property>
    </bean>

    <!-- Transaction Management -->
    <tx:annotation-driven transaction-manager="txManager"
        proxy-target-class="true" />

    <bean id="txManager"
        class="org.springframework.orm.hibernate4.HibernateTransactionManager">
        <property name="sessionFactory" ref="sessionFactory" />
    </bean>
</beans>
```

A whole lot of stuff is going on here, so let's go through it.

There are two package-scan configurations. Adjust this to your needs before using the template.

Spring tries to load file `application.properties` from classpath. Its purpose is to to
override defaults. All property placeholders have a default value,
e.g. `${hibernate.show_sql:false}`. Everything after first colon is
used as default value. If you want for example show sql statements,
add `hibernate.show_sql=true` to application.properties and put in in
`/src/main/resources` directory. No need to touch xml file.

HSQLDB is set up as database. By default, an in-memory database is used, i.e. it is empty every time the
application is started. If you want to keep your data, configure file
storage for database by setting property `jdb.url` to something
like `jdbc:hsqldb:file:/your/home/db/myApp`.


## Example

Here is a tiny example that shows how to use the template. First let's
create a Person class that is mapped to database.

```java
package df.ormtemplate.domain

import groovy.transform.ToString;

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
@ToString(includeNames=true)
class Person {
    @Id @GeneratedValue
    long id

    String firstName
    String lastName

    Date creationDate = new Date()
}
``` 

Adding @Entity annotation and configuring an Id is all that's required.
Thanks to Groovy we don't even need getters and setters.

The main class initializes Spring's application context, saves some entries in database
and searches for them.

```java
package df.ormtemplate

import df.ormtemplate.domain.Person
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.criterion.Example
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.ClassPathXmlApplicationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class Main {
    @Autowired
    private SessionFactory sf

    static main(args) {
        new ClassPathXmlApplicationContext("applicationContext.xml").getBean(Main).run()
    }

    def run() {
        Session session = sf.getCurrentSession()

        //Create test data
        session.saveOrUpdate(new Person(firstName: "Leonard", lastName: "Hofstadter"))
        session.saveOrUpdate(new Person(firstName: "Sheldon", lastName: "Cooper"))
        session.saveOrUpdate(new Person(firstName: "Howard", lastName: "Wolowitz"))
        session.saveOrUpdate(new Person(firstName: "Rajesh", lastName: "Koothrappali"))

        //Read and print table content
        println "TABLE CONTENT:"
        session.createQuery("from Person").list().each { println it }
    }
}
```

Main class is annotated with @Component to make it a Spring bean.
Otherwise we could not inject other beans so easily. @Transactional is
required because database operations are performed and they need a
transaction context.

First the Spring application context is initialized from
xml file, retrieves the main class from context and calls run() method.
By using the Spring bean, autowiring is supported in main class, as used
for SessionFactory in this example.

Method run() gets a
session from SessionFactory. By using getCurrentSession(), the session
does not need to be closed manually after work is done. 
Then some famous geeks are stored in database. Then all Persons are read from
database and printed.

## Troubleshooting

If hundreds of DEBUG log messages are printed on screen when running the
example, you most likely compiled logback.groovy (or Eclipse did that
for you). But it has to remain a Groovy file. In Eclipse you can
configure that all groovy files in /src/main/resources are not compiled,
but just copied. It's done in preferences. Go to Groovy/Compiler and
activate "Enable script folder support".

That's all. I hope you find the
template as useful as I do. Do you have any tips and tricks on how to
improve it even further?