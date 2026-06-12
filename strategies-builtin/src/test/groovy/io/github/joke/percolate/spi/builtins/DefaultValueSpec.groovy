package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Frontiers
import io.github.joke.percolate.spi.builtins.test.Renders
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class DefaultValueSpec extends Specification {

    def ctx = new ResolveCtxBuilder().build()
    def types = TypeUniverse.types()
    def optionalOfString = types.getDeclaredType(TypeUniverse.element('java.util.Optional'), TypeUniverse.STRING)

    def 'fires only with a present default'() {
        expect:
        new DefaultValue().expand(Frontiers.forTarget(TypeUniverse.STRING, [TypeUniverse.STRING]), ctx).toList().empty
    }

    def 'coalesces a nullable reference scalar with requireNonNullElse, evaluating the source once'() {
        when:
        def steps = new DefaultValue()
                .expand(Frontiers.withDefault(TypeUniverse.STRING, 'unknown', [TypeUniverse.STRING]), ctx)
                .toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.CONVERSION
        steps[0].weight == Weights.NOOP
        types.isSameType(steps[0].inputs[0].type, TypeUniverse.STRING)
        types.isSameType(steps[0].output, TypeUniverse.STRING)
        Renders.edge(steps[0].codegen, 'name') == 'java.util.Objects.requireNonNullElse(name, "unknown")'
    }

    def 'coalesces an Optional source with orElse'() {
        when:
        def steps = new DefaultValue()
                .expand(Frontiers.withDefault(TypeUniverse.STRING, 'unknown', [optionalOfString]), ctx)
                .toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.CONVERSION
        types.isSameType(steps[0].inputs[0].type, optionalOfString)
        types.isSameType(steps[0].output, TypeUniverse.STRING)
        Renders.edge(steps[0].codegen, 'in.name()') == 'in.name().orElse("unknown")'
    }

    def 'reuses constant coercion to the target type'() {
        when:
        def steps = new DefaultValue()
                .expand(Frontiers.withDefault(TypeUniverse.INTEGER, '0', [TypeUniverse.INTEGER]), ctx)
                .toList()

        then:
        steps.size() == 1
        Renders.edge(steps[0].codegen, 'age') == 'java.util.Objects.requireNonNullElse(age, java.lang.Integer.valueOf(0))'
    }

    def 'emits nothing for a primitive source (a primitive can never be absent)'() {
        expect:
        new DefaultValue().expand(Frontiers.withDefault(TypeUniverse.INT, '0', [TypeUniverse.INT]), ctx).toList().empty
    }

    def 'emits nothing for an uncoercible default'() {
        expect:
        new DefaultValue().expand(Frontiers.withDefault(TypeUniverse.INT, 'abc', [TypeUniverse.INT]), ctx).toList().empty
    }
}
