package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.InputDecl
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.Value
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
 * {@link SourcePathDescender} unit-tested mock-only: {@code materialiseRoot} and {@code descend} directly (no
 * sibling calls), {@code pinnedSource} and {@code descendSegment} via a {@code Spy} isolating their sibling
 * delegation (design D1/D2 of change {@code forward-target-bound-accessor-descent}, decomposed by
 * {@code decompose-engine-stages}).
 */
@Tag('unit')
class SourcePathDescenderSpec extends Specification {

    List<ExpansionStrategy> strategies = []
    ResolveCtx resolveCtx = Mock()
    NullabilityResolver resolver = Mock()
    MapperGraph graph = Mock()
    Applier applier = Mock()
    OperationLander operationLander = Mock()

    // ---- pinnedSource: forward descent from the scope-input root, isolated via Spy from its siblings ------------

    def 'pinnedSource with no segments pins nothing, touching no collaborator'() {
        def descender = spyDescender()
        Scope scope = Mock()

        when:
        def result = descender.pinnedSource(scope, [])

        then:
        1 * descender._
        0 * _

        expect:
        result == null
    }

    def 'pinnedSource with a single segment returns just the materialised root'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value root = Mock()

        when:
        def result = descender.pinnedSource(scope, ['person'])

        then:
        1 * descender.materialiseRoot(scope, 'person') >> root
        1 * descender._
        0 * _

        expect:
        result.is(root)
    }

    def 'pinnedSource returns null immediately when the root does not materialise, without descending'() {
        def descender = spyDescender()
        Scope scope = Mock()

        when:
        def result = descender.pinnedSource(scope, ['ghost', 'firstName'])

        then:
        1 * descender.materialiseRoot(scope, 'ghost') >> null
        1 * descender._
        0 * _

        expect:
        result == null
    }

    def 'pinnedSource with multiple segments descends each further segment from the materialised root'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value root = Mock()
        Value leaf = Mock()

        when:
        def result = descender.pinnedSource(scope, ['person', 'firstName'])

        then:
        1 * descender.materialiseRoot(scope, 'person') >> root
        1 * descender.descendSegment(scope, root, ['person', 'firstName']) >> leaf
        1 * descender._
        0 * _

        expect:
        result.is(leaf)
    }

    def 'pinnedSource stops descending the moment a segment fails to resolve'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value root = Mock()

        when:
        def result = descender.pinnedSource(scope, ['person', 'ghost', 'x'])

        then:
        1 * descender.materialiseRoot(scope, 'person') >> root
        1 * descender.descendSegment(scope, root, ['person', 'ghost']) >> null
        1 * descender._
        0 * _

        expect:
        result == null
    }

    // ---- materialiseRoot: the scope-input LEAF for the path's first segment ---------------------------------------

    def 'materialiseRoot applies the matching input declaration as a Value'() {
        def descender = descender()
        Scope scope = Mock()
        InputDecl decl = Mock()
        Location loc = Mock()
        TypeMirror type = Mock()
        Value root = Mock()

        when:
        def result = descender.materialiseRoot(scope, 'person')

        then:
        1 * scope.inputDecls(_) >> Stream.of(decl)
        decl.location >> loc
        loc.slotName() >> 'person'
        decl.type >> type
        decl.nullness >> Nullability.NON_NULL
        1 * applier.apply(graph, new AddValue(scope, loc, type, Nullability.NON_NULL)) >> root
        0 * _

        expect:
        result.is(root)
    }

    def 'materialiseRoot returns null when no input declares the segment'() {
        def descender = descender()
        Scope scope = Mock()
        InputDecl decl = Mock()
        Location loc = Mock()

        when:
        def result = descender.materialiseRoot(scope, 'ghost')

        then:
        1 * scope.inputDecls(_) >> Stream.of(decl)
        decl.location >> loc
        loc.slotName() >> 'person'
        0 * _

        expect:
        result == null
    }

    // ---- descendSegment: lands every accessor for the segment, isolated via Spy from its sibling `descend` -------

    def 'descendSegment lands the accessor and returns its produced source Value'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value parent = Mock()
        TypeMirror parentType = Mock()
        TypeMirror outputType = Mock()
        Codegen codegen = Mock()
        def port = new Port('self', parentType, Nullability.NON_NULL)
        def spec = OperationSpec.of('getFirstName', codegen, 1, [port], outputType, Nullability.NON_NULL)
        def childLoc = new SourceLocation(new AccessPath(['person', 'firstName']))
        def expectedOutput = new AddValue(scope, childLoc, outputType, Nullability.NON_NULL)
        def reused = new AddValue(scope, Mock(Location), parentType, Nullability.NON_NULL)
        Operation operation = Mock()
        Value source = Mock()

        when:
        def result = descender.descendSegment(scope, parent, ['person', 'firstName'])

        then:
        parent.type() >> parentType
        parent.nullness() >> Nullability.NON_NULL
        1 * descender.descend({ it.parentType() == parentType && it.parentNullness() == Nullability.NON_NULL &&
                it.segment() == 'firstName' }, resolveCtx) >> [spec]
        1 * operationLander.reuse(parent) >> reused
        1 * operationLander.landOperation(spec, [new PortBinding(port, reused)], expectedOutput) >> operation
        1 * graph.outputOf(operation) >> Optional.of(source)
        1 * descender._
        0 * _

        expect:
        result.is(source)
    }

    def 'descendSegment lands every deduplicated accessor but returns only the first-produced source Value'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value parent = Mock()
        TypeMirror parentType = Mock()
        TypeMirror outputType = Mock()
        Codegen codegen = Mock()
        def port = new Port('self', parentType, Nullability.NON_NULL)
        def getter = OperationSpec.of('getFirstName', codegen, 1, [port], outputType, Nullability.NON_NULL)
        def field = OperationSpec.of('firstNameField', codegen, 1, [port], outputType, Nullability.NON_NULL)
        def reused = new AddValue(scope, Mock(Location), parentType, Nullability.NON_NULL)
        Operation firstOperation = Mock()
        Operation secondOperation = Mock()
        Value firstSource = Mock()
        Value secondSource = Mock()

        when:
        def result = descender.descendSegment(scope, parent, ['person', 'firstName'])

        then:
        parent.type() >> parentType
        parent.nullness() >> Nullability.NON_NULL
        1 * descender.descend({ it.segment() == 'firstName' }, resolveCtx) >> [getter, field]
        operationLander.reuse(parent) >> reused
        1 * operationLander.landOperation(getter, _, _) >> firstOperation
        1 * operationLander.landOperation(field, _, _) >> secondOperation
        1 * graph.outputOf(firstOperation) >> Optional.of(firstSource)
        0 * graph.outputOf(secondOperation)
        1 * descender._
        0 * _

        expect:
        result.is(firstSource)
        firstSource != secondSource
    }

    def 'descendSegment deduplicates accessors sharing a structural signature, landing only one operation'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value parent = Mock()
        TypeMirror parentType = Mock()
        TypeMirror outputType = Mock()
        Codegen codegen = Mock()
        def port = new Port('self', parentType, Nullability.NON_NULL)
        def spec = OperationSpec.of('getFirstName', codegen, 1, [port], outputType, Nullability.NON_NULL)
        def duplicateSpec = OperationSpec.of('getFirstName', codegen, 9, [port], outputType, Nullability.NON_NULL)
        def reused = new AddValue(scope, Mock(Location), parentType, Nullability.NON_NULL)
        Operation operation = Mock()
        Value source = Mock()

        when:
        def result = descender.descendSegment(scope, parent, ['person', 'firstName'])

        then:
        parent.type() >> parentType
        parent.nullness() >> Nullability.NON_NULL
        1 * descender.descend({ it.segment() == 'firstName' }, resolveCtx) >> [spec, duplicateSpec]
        operationLander.reuse(parent) >> reused
        1 * operationLander.landOperation(spec, _, _) >> operation
        1 * graph.outputOf(operation) >> Optional.of(source)
        1 * descender._
        0 * _

        expect:
        result.is(source)
    }

    def 'descendSegment returns null when no accessor resolves the segment'() {
        def descender = spyDescender()
        Scope scope = Mock()
        Value parent = Mock()
        TypeMirror parentType = Mock()

        when:
        def result = descender.descendSegment(scope, parent, ['person', 'firstName'])

        then:
        parent.type() >> parentType
        parent.nullness() >> Nullability.NON_NULL
        1 * descender.descend({ it.segment() == 'firstName' }, resolveCtx) >> []
        1 * descender._
        0 * _

        expect:
        result == null
    }

    // ---- descend: queries every strategy for one accessor demand, no sibling call ----------------------------------

    def 'descend queries every strategy for one accessor demand'() {
        ExpansionStrategy strategy0 = Mock()
        ExpansionStrategy strategy1 = Mock()
        def descender = new SourcePathDescender([strategy0, strategy1], resolveCtx, resolver, graph, applier, operationLander)
        DescendView demand = Mock()
        Codegen codegen = Mock()
        TypeMirror type = Mock()
        def spec0 = OperationSpec.of('a', codegen, 1, [], type, Nullability.NON_NULL)
        def spec1 = OperationSpec.of('b', codegen, 1, [], type, Nullability.NON_NULL)

        when:
        def result = descender.descend(demand, resolveCtx)

        then:
        1 * strategy0.descend(demand, resolveCtx) >> Stream.of(spec0)
        1 * strategy1.descend(demand, resolveCtx) >> Stream.of(spec1)
        0 * _

        expect:
        result == [spec0, spec1]
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private SourcePathDescender descender() {
        new SourcePathDescender(strategies, resolveCtx, resolver, graph, applier, operationLander)
    }

    private SourcePathDescender spyDescender() {
        Spy(SourcePathDescender, constructorArgs: [strategies, resolveCtx, resolver, graph, applier, operationLander])
    }
}
