package io.github.joke.percolate.docs.pathaccess

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's Path-access section (nested-paths.adoc). {@code ContactMapper} is real source compiled
 * by the ordinary {@code compileTestJava} task through the real starter — no compile-testing. One example
 * witnesses all three source-path accessor forms: a JavaBeans getter, a record accessor, and a public field.
 */
@Tag('integration')
class PathAccessDocExampleSpec extends Specification {

    ContactMapper mapper = new ContactMapperImpl()

    def 'email, areaCode, and zip resolve via a getter, a record accessor, and a public field respectively'() {
        def contact = new Contact('ada@example.com', new Phone('020'), '90210')

        when:
        def view = mapper.map(contact)

        then:
        view.email == 'ada@example.com'
        view.areaCode == '020'
        view.zip == '90210'
    }
}
