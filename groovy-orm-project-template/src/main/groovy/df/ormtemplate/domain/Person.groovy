package df.ormtemplate.domain

import groovy.transform.ToString;

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
@ToString(includeNames=true)
class Person {
	@Id	@GeneratedValue
	long id
	
	String firstName
	String lastName
	
	Date creationDate = new Date()
}
