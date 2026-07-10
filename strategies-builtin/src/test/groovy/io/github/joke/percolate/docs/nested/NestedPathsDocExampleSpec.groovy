package io.github.joke.percolate.docs.nested

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's nested-paths page. {@code ProfileMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. The source path chains three
 * accessor hops; the target path builds the intermediate {@code Location}.
 */
@Tag('integration')
class NestedPathsDocExampleSpec extends Specification {

    def 'map chains the source accessors and builds the nested target'() {
        def mapper = new ProfileMapperImpl()
        def user = new User(new Company(new Address('Springfield')))

        expect:
        mapper.map(user).location.city == 'Springfield'
    }
}
