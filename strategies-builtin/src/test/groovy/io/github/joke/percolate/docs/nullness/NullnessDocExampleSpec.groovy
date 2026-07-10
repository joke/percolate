package io.github.joke.percolate.docs.nullness

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's Defaults & nullness chapter. {@code OrderMapper} is real source compiled by the
 * ordinary {@code compileTestJava} task through the real starter — no compile-testing. Witnesses the
 * {@code NULLABLE -> NON_NULL} crossing guard: a {@code @Nullable}-declared source with no {@code defaultValue}
 * is guarded by a {@code requireNonNull} check naming the target slot.
 */
@Tag('integration')
class NullnessDocExampleSpec extends Specification {

    OrderMapper mapper = new OrderMapperImpl()

    def 'a present trackingCode flows through unguarded'() {
        expect:
        mapper.map(new OrderForm('ABC123')).trackingCode == 'ABC123'
    }

    def 'an absent trackingCode throws, naming the slot in the message'() {
        when:
        mapper.map(new OrderForm(null))

        then:
        def e = thrown(NullPointerException)
        e.message == "source for slot 'trackingCode' is null but target is non-null"
    }
}
