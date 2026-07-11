package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.model.GoalSpec
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * {@link TargetProducer} unit-tested mock-only: enumerates the grounded specs a FREE target demand admits (design
 * D6/D9 of change {@code target-driven-engine}, decomposed out of {@code ExpandStage.Driver.expandFree} by
 * {@code decompose-engine-stages}).
 */
@Tag('unit')
class TargetProducerSpec extends Specification {

    ExpansionStrategy strategy = Mock()
    SourceCandidates sourceCandidates = Mock()
    Grounding grounding = Mock()
    ResolveCtx resolveCtx = Mock()
    NullabilityResolver resolver = Mock()
    Codegen codegen = Mock()
    TypeMirror valueType = Mock()

    // ---- produce: demand construction, strategy query, grounding, dedup ------------------------------------------

    def 'produce queries the strategies, grounds each spec, and deduplicates'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of('address'))
        Value value = Mock()
        def producer = new TargetProducer([strategy], [(scope): GoalSpec.from([])], sourceCandidates, grounding,
                resolveCtx, resolver)
        def spec0 = OperationSpec.of('a', codegen, 1, [], valueType, Nullability.NON_NULL)
        def spec1 = OperationSpec.of('b', codegen, 1, [], valueType, Nullability.NON_NULL)
        def sourceTypes = [Mock(TypeMirror)]

        when:
        def result = producer.produce(value)

        then:
        value.scope >> scope
        value.loc >> loc
        value.type() >> valueType
        value.nullness() >> Nullability.NON_NULL
        1 * strategy.expand({ it.targetType() == valueType && it.targetNullness() == Nullability.NON_NULL &&
                !it.directive().present && it.declaredChildren().empty && it.bindingName() == 'address' },
                resolveCtx) >> Stream.of(spec0)
        1 * sourceCandidates.sourceTypes(scope) >> sourceTypes
        1 * grounding.ground(spec0, sourceTypes) >> Stream.of(spec0, spec1)
        0 * _

        expect:
        result == [spec0, spec1]
    }

    def 'produce derives the demand\'s directive and declared children from the goal spec'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of('address'))
        Value value = Mock()
        def goalSpecs = [(scope): GoalSpec.from([directive('address', 'home.street'), directive('address.city', null)])]
        def producer = new TargetProducer([strategy], goalSpecs, sourceCandidates, grounding, resolveCtx, resolver)

        when:
        producer.produce(value)

        then:
        value.scope >> scope
        value.loc >> loc
        value.type() >> valueType
        value.nullness() >> Nullability.NON_NULL
        1 * strategy.expand({ it.directive().present && it.directive().get().sourcePath() == ['home', 'street'] &&
                it.declaredChildren() == ['city'].toSet() }, resolveCtx) >> Stream.empty()
        1 * sourceCandidates.sourceTypes(scope) >> []
        0 * _
    }

    def 'produce deduplicates grounded specs sharing a structural signature'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of(''))
        Value value = Mock()
        def producer = new TargetProducer([strategy], [(scope): GoalSpec.from([])], sourceCandidates, grounding,
                resolveCtx, resolver)
        def port = new Port('x', valueType, Nullability.NON_NULL)
        def spec = OperationSpec.of('dup', codegen, 1, [port], valueType, Nullability.NON_NULL)
        def sameSignature = OperationSpec.of('dup', codegen, 9, [port], valueType, Nullability.NON_NULL)

        when:
        def result = producer.produce(value)

        then:
        value.scope >> scope
        value.loc >> loc
        value.type() >> valueType
        value.nullness() >> Nullability.NON_NULL
        1 * strategy.expand(_, resolveCtx) >> Stream.of(spec)
        1 * sourceCandidates.sourceTypes(scope) >> []
        1 * grounding.ground(spec, []) >> Stream.of(spec, sameSignature)
        0 * _

        expect:
        result == [spec]
    }

    // ---- pinnedSourcePath: the directive-pinned source segments at the value's target path ------------------------

    def 'pinnedSourcePath returns the split source segments of the binding at the target path'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of('address'))
        Value value = Mock()
        value.scope >> scope
        value.loc >> loc
        def producer = new TargetProducer([strategy], [(scope): GoalSpec.from([directive('address', 'home.street')])],
                sourceCandidates, grounding, resolveCtx, resolver)

        expect:
        producer.pinnedSourcePath(value) == ['home', 'street']
    }

    def 'pinnedSourcePath is empty when the binding has no source'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of('address'))
        Value value = Mock()
        value.scope >> scope
        value.loc >> loc
        def directive = new MappingDirective('address', null, 'literal', null, null, null, null, null, null, null, null, null, null)
        def producer = new TargetProducer([strategy], [(scope): GoalSpec.from([directive])], sourceCandidates,
                grounding, resolveCtx, resolver)

        expect:
        producer.pinnedSourcePath(value).empty
    }

    def 'pinnedSourcePath is empty when the value has no binding'() {
        Scope scope = Mock()
        def loc = new TargetLocation(TargetPath.of('ghost'))
        Value value = Mock()
        value.scope >> scope
        value.loc >> loc
        def producer = new TargetProducer([strategy], [(scope): GoalSpec.from([])], sourceCandidates, grounding,
                resolveCtx, resolver)

        expect:
        producer.pinnedSourcePath(value).empty
    }

    // ---- run: queries every strategy for one demand ---------------------------------------------------------------

    def 'run queries every strategy for one demand'() {
        ExpansionStrategy strategy0 = Mock()
        ExpansionStrategy strategy1 = Mock()
        def producer = new TargetProducer([strategy0, strategy1], [:], sourceCandidates, grounding, resolveCtx, resolver)
        DemandView demand = Mock()
        def spec0 = OperationSpec.of('a', codegen, 1, [], valueType, Nullability.NON_NULL)
        def spec1 = OperationSpec.of('b', codegen, 1, [], valueType, Nullability.NON_NULL)

        when:
        def result = producer.run(demand, resolveCtx)

        then:
        1 * strategy0.expand(demand, resolveCtx) >> Stream.of(spec0)
        1 * strategy1.expand(demand, resolveCtx) >> Stream.of(spec1)
        0 * _

        expect:
        result == [spec0, spec1]
    }

    // ---- dedup / signature: static structural utilities -------------------------------------------------------------

    def 'dedup drops duplicate structural signatures, preserving first-seen order'() {
        def port = new Port('x', valueType, Nullability.NON_NULL)
        def specA = OperationSpec.of('op', codegen, 1, [port], valueType, Nullability.NON_NULL)
        def specDup = OperationSpec.of('op', codegen, 2, [port], valueType, Nullability.NON_NULL)
        def specB = OperationSpec.of('other', codegen, 1, [port], valueType, Nullability.NON_NULL)

        expect:
        TargetProducer.dedup([specA, specDup, specB]) == [specA, specB]
    }

    def 'signature combines label, output type, and port shapes'() {
        def port = new Port('x', valueType, Nullability.NON_NULL)
        def spec = OperationSpec.of('op', codegen, 1, [port], valueType, Nullability.NON_NULL)

        expect:
        TargetProducer.signature(spec) == "op|${valueType}|x:${valueType}:${Nullability.NON_NULL}".toString()
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, null, null, null, null, null, null, null, null, null, null, null)
    }
}
