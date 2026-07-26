package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * {@link Offer} is the pseudo-sealed answer shape an {@link ExpansionStrategy} makes for one demand (design D1 of
 * change {@code decouple-engine-from-strategy-semantics}): a {@link Offer.Production} carrying an
 * {@link OperationSpec}, or a {@link Offer.Refusal} carrying a {@link Subject} and a message. Each leaf is a Lombok
 * {@code @Value}, so equality is structural.
 */
@Tag('unit')
class OfferSpec extends Specification {

    TypeMirror type = Mock()
    Codegen codegen = Mock()
    OperationSpec spec = OperationSpec.of('label', codegen, 0, [], type, Nullability.NON_NULL)
    Subject subject = Mock()

    def 'of wraps the given spec in a Production'() {
        expect:
        Offer.of(spec) == new Offer.Production(spec)
    }

    def 'refusal wraps the given subject and message in a Refusal'() {
        expect:
        Offer.refusal(subject, 'cannot coerce') == new Offer.Refusal(subject, 'cannot coerce')
    }

    def 'two Production instances over the same spec are equal; over different specs are not'() {
        def otherSpec = OperationSpec.of('other', codegen, 0, [], type, Nullability.NON_NULL)

        expect:
        Offer.of(spec) == Offer.of(spec)
        Offer.of(spec) != Offer.of(otherSpec)
    }

    def 'two Refusal instances over the same subject and message are equal; over different ones are not'() {
        Subject otherSubject = Mock()

        expect:
        Offer.refusal(subject, 'x') == Offer.refusal(subject, 'x')
        Offer.refusal(subject, 'x') != Offer.refusal(otherSubject, 'x')
        Offer.refusal(subject, 'x') != Offer.refusal(subject, 'y')
    }
}
