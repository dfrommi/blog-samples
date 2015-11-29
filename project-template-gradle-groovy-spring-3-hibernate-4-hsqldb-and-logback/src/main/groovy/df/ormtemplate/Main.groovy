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

		//Do a query by example
		println "QUERY BY EXAMPLE"
		Person examplePerson = new Person(lastName: "cooper")
		Example example = Example.create(examplePerson).ignoreCase().excludeProperty("creationDate")
		session.createCriteria(Person).add(example).list().each { println it }
	}
}
