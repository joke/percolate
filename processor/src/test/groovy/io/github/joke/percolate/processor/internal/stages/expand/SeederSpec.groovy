package io.github.joke.percolate.processor.internal.stages.expand

import io.github.joke.percolate.Ambient
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
 * change {@code decompose-engine-stages}).
 */
@Tag('unit')
class SeederSpec extends Specification {

    MapperGraph graph = Mock()
    Applier applier = Mock()
    NullabilityResolver resolver = Mock()
    Seeder seeder = new Seeder(graph, applier, resolver)

    def 'seed mints and marks the return-root Value, typed and nulled from the method return declaration'() {
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

    def 'declarationsFor builds one resolved InputDecl per parameter, LOCAL by default and INHERITED for @Ambient'() {
        ExecutableElement method = Mock()
        VariableElement plain = Mock()
        VariableElement ambient = Mock()
        TypeMirror plainType = Mock()
        TypeMirror ambientType = Mock()
        method.parameters >> [plain, ambient]
        plain.simpleName >> nameOf('customer')
        plain.asType() >> plainType
        plain.getAnnotation(Ambient) >> null
        ambient.simpleName >> nameOf('order')
        ambient.asType() >> ambientType
        ambient.getAnnotation(Ambient) >> ([value: { -> '' }] as Ambient)
        resolver.resolve(plainType, plain) >> Nullability.NON_NULL
        resolver.resolve(ambientType, ambient) >> Nullability.NULLABLE

        expect:
        seeder.declarationsFor(method) == [
                new InputDecl(new SourceLocation(AccessPath.of('customer')), plainType, Nullability.NON_NULL,
                        'customer', Visibility.LOCAL),
                new InputDecl(new SourceLocation(AccessPath.of('order')), ambientType, Nullability.NULLABLE,
                        'order', Visibility.INHERITED)
        ]
    }

    private static Name nameOf(final String value) {
        [toString: { -> value }] as Name
    }
}
