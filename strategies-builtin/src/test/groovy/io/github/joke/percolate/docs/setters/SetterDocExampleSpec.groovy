package io.github.joke.percolate.docs.setters

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's setter-assembly page. {@code SetterMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Exercises the JavaBean form end to
 * end: the containment gate that leaves surplus setters uncalled, inherited setters, and a {@code this}-returning
 * setter whose result the generated helper discards.
 */
@Tag('integration')
class SetterDocExampleSpec extends Specification {

    def mapper = new SetterMapperImpl()

    def 'a plain JavaBean assembles through its no-argument constructor and its setters'() {
        def person = mapper.toPerson(new PersonDto('Ada', 36))

        expect:
        person.name == 'Ada'
        person.age == 36
    }

    def 'only the declared children are set, so surplus bean setters are left uncalled'() {
        def person = mapper.toNameOnlyPerson(new PersonDto('Ada', 36))

        expect:
        person.name == 'Ada'
        person.age == 0
        person.nickname == 'unset'
    }

    def 'an inherited setter is matched alongside a declared one'() {
        def employee = mapper.toEmployee(new EmployeeDto('Ada', 90_000L))

        expect:
        employee.name == 'Ada'
        employee.salary == 90_000L
    }

    def 'a this-returning setter assembles, and its result is discarded'() {
        expect:
        mapper.toAccount(new AccountDto('ada')).owner == 'ada'
    }
}
