package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class NullabilitySpec extends Specification {

    def 'enum declares exactly three constants in documented order'() {
        expect:
        Nullability.values() == [
                Nullability.NULLABLE,
                Nullability.NON_NULL,
                Nullability.UNKNOWN,
        ] as Nullability[]
    }

    def 'join absorbs NULLABLE on the left'() {
        expect:
        Nullability.join(Nullability.NULLABLE, other) == Nullability.NULLABLE

        where:
        other << [Nullability.NULLABLE, Nullability.NON_NULL, Nullability.UNKNOWN]
    }

    def 'join absorbs NULLABLE on the right'() {
        expect:
        Nullability.join(other, Nullability.NULLABLE) == Nullability.NULLABLE

        where:
        other << [Nullability.NULLABLE, Nullability.NON_NULL, Nullability.UNKNOWN]
    }

    def 'join propagates uncertainty when NON_NULL meets UNKNOWN'() {
        expect:
        Nullability.join(Nullability.NON_NULL, Nullability.UNKNOWN) == Nullability.UNKNOWN
        Nullability.join(Nullability.UNKNOWN, Nullability.NON_NULL) == Nullability.UNKNOWN
    }

    def 'join of two equal non-NULLABLE values is identity'() {
        expect:
        Nullability.join(Nullability.NON_NULL, Nullability.NON_NULL) == Nullability.NON_NULL
        Nullability.join(Nullability.UNKNOWN, Nullability.UNKNOWN) == Nullability.UNKNOWN
    }
}
