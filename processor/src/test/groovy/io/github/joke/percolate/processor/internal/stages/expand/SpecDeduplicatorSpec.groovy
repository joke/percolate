package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

/**
 * The structural dedup both {@link TargetProducer} and {@link SourcePathDescender} apply to an over-emitted offer
 * set. Lifted out of {@code TargetProducerSpec} by change {@code tighten-testability-conventions}, when the two
 * statics became instance methods on a collaborator both callers hold.
 */
@Tag('unit')
class SpecDeduplicatorSpec extends Specification {

    SpecDeduplicator deduplicator = new SpecDeduplicator()

    Codegen codegen = Mock()
    TypeMirror valueType = Mock()

    def 'dedup drops duplicate structural signatures, preserving first-seen order'() {
        def port = new Port('x', valueType, Nullability.NON_NULL)
        def specA = OperationSpec.of('op', codegen, 1, [port], valueType, Nullability.NON_NULL)
        def specDup = OperationSpec.of('op', codegen, 2, [port], valueType, Nullability.NON_NULL)
        def specB = OperationSpec.of('other', codegen, 1, [port], valueType, Nullability.NON_NULL)

        expect:
        deduplicator.dedup([specA, specDup, specB]) == [specA, specB]
    }

    def 'dedup of an empty offer set is empty'() {
        expect:
        deduplicator.dedup([]).empty
    }

    def 'signature combines label, output type, and port shapes'() {
        def port = new Port('x', valueType, Nullability.NON_NULL)
        def spec = OperationSpec.of('op', codegen, 1, [port], valueType, Nullability.NON_NULL)

        expect:
        deduplicator.signature(spec) == "op|${valueType}|x:${valueType}:${Nullability.NON_NULL}".toString()
    }

    def 'signature of a zero-port spec carries an empty port section'() {
        def spec = OperationSpec.of('op', codegen, 1, [], valueType, Nullability.NON_NULL)

        expect:
        deduplicator.signature(spec) == "op|${valueType}|".toString()
    }
}
