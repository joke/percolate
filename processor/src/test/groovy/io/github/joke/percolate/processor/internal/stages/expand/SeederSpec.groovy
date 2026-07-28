package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.InputDecl
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.internal.graph.Visibility
import io.github.joke.percolate.processor.model.GoalSpec
import io.github.joke.percolate.processor.model.GoalSpecFactory
import io.github.joke.percolate.processor.model.ScopeInputOverride
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.Nullability
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link Seeder} unit-tested by mocking {@link MapperGraph}/{@link Applier}/{@link NullabilityResolver} — mints and
 * marks one method's return-root {@code Value} (decomposed out of {@code ExpandStage.Driver.seedReturnRoot} by
 * change {@code decompose-engine-stages}). A parameter's name/visibility default to its own simple name/
 * {@link Visibility#LOCAL} unless the method's {@code GoalSpec} carries a published {@link ScopeInputOverride}
 * (design D5/D7 of change {@code decouple-engine-from-strategy-semantics}) — {@code Seeder} reads no annotation.
 */
@Tag('unit')
class SeederSpec extends Specification {

    MapperGraph graph = Mock()
    Applier applier = Mock()
    NullabilityResolver resolver = Mock()

    def 'seed mints and marks the return-root Value, typed and nulled from the method return declaration'() {
        def seeder = new Seeder(graph, applier, resolver, [:])
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        Value root = Mock()

        when:
        def result = seeder.seed(method)

        then:
        1 * method.parameters >> []
        1 * method.returnType >> returnType
        1 * resolver.resolve(returnType, method) >> Nullability.NON_NULL
        1 * applier.apply(graph, new AddValue(new MethodScope(method), new TargetLocation(TargetPath.of('')),
                returnType, Nullability.NON_NULL)) >> root
        1 * graph.markReturnRoot(root)
        0 * _

        expect:
        result.is(root)
    }

    def 'declarationsFor builds one resolved InputDecl per parameter, LOCAL by default with its own simple name'() {
        def seeder = new Seeder(graph, applier, resolver, [:])
        ExecutableElement method = Mock()
        VariableElement plain = Mock()
        TypeMirror plainType = Mock()
        method.parameters >> [plain]
        plain.simpleName >> nameOf('customer')
        plain.asType() >> plainType
        resolver.resolve(plainType, plain) >> Nullability.NON_NULL

        expect:
        seeder.declarationsFor(method) == [
                new InputDecl(new SourceLocation(AccessPath.of('customer')), plainType, Nullability.NON_NULL,
                        'customer', Visibility.LOCAL)
        ]
    }

    def 'declarationsFor honours a published scope-input override, e.g. an @Ambient rename to INHERITED'() {
        ExecutableElement method = Mock()
        VariableElement ambient = Mock()
        TypeMirror ambientType = Mock()
        method.parameters >> [ambient]
        ambient.simpleName >> nameOf('order')
        ambient.asType() >> ambientType
        resolver.resolve(ambientType, ambient) >> Nullability.NULLABLE
        def override = new ScopeInputOverride(ambient, 'order', io.github.joke.percolate.spi.Visibility.INHERITED)
        def goalSpecs = [(new MethodScope(method)): new GoalSpecFactory().from([], [:], [:], [override])]
        def seeder = new Seeder(graph, applier, resolver, goalSpecs)

        expect:
        seeder.declarationsFor(method) == [
                new InputDecl(new SourceLocation(AccessPath.of('order')), ambientType, Nullability.NULLABLE,
                        'order', Visibility.INHERITED)
        ]
    }

    private static Name nameOf(final String value) {
        [toString: { -> value }] as Name
    }
}
