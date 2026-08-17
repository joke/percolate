package io.github.joke.percolate.docs.builders

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's builder-assembly page. {@code BuilderMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Exercises each shipped convention
 * end to end and the containment gate that lets a builder with surplus setters still apply.
 */
@Tag('integration')
class BuilderDocExampleSpec extends Specification {

    def mapper = new BuilderMapperImpl()

    def 'a fluent builder assembles the target from the declared children'() {
        def person = mapper.toPerson(new PersonDto('Ada', 36))

        expect:
        person.name == 'Ada'
        person.age == 36
    }

    def 'a protobuf-style builder assembles through newBuilder and setX'() {
        expect:
        mapper.toMessage(new MessageDto('release')).subject == 'release'
    }

    def 'a with-style builder assembles through withX'() {
        expect:
        mapper.toAccount(new AccountDto('ada')).owner == 'ada'
    }

    def 'a side-located builder assembles by constructing the sibling builder'() {
        expect:
        mapper.toWidget(new WidgetDto('gauge')).label == 'gauge'
    }

    def 'only the declared children are set, so surplus builder setters are left untouched'() {
        def person = mapper.toNameOnlyPerson(new PersonDto('Ada', 36))

        expect:
        person.name == 'Ada'
        person.age == 0
        person.nickname == 'unset'
    }
}
