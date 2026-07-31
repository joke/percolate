package com.example.smoke

import spock.lang.Specification
import spock.lang.Tag

// Runs the generated PersonMapperImpl and checks the result. Referencing the generated type and running it on a
// classpath that carries no percolate artifact is the black-box assertion: the published path produced a working,
// zero-footprint mapper.
@Tag('integration')
class PersonMapperSmokeSpec extends Specification {

    def 'maps a person onto a human'() {
        def human = new PersonMapperImpl().map(new Person('Alice', 30))

        expect:
        verifyAll(human) {
            firstName == 'Alice'
            age == 30
        }
    }
}
