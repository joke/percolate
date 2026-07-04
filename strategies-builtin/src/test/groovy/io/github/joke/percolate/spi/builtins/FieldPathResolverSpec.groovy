package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.builtins.test.Demands
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class FieldPathResolverSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()
    @Shared def ctx = new ResolveCtxBuilder(javac).build()
    @Shared def box = javac.of(io.github.joke.percolate.spi.builtins.fixtures.BoxFixture).asType()

    def 'matches a public, non-static field as a unary accessor operation typed to the field type'() {
        when:
        def specs = new FieldPathResolver().descend(Demands.descend(box, 'value'), ctx).toList()

        then:
        specs.size() == 1
        def spec = specs[0]
        spec.childScope.empty
        spec.codegen instanceof OperationCodegen
        spec.weight == Weights.STEP_FIELD
        spec.ports.size() == 1
        spec.ports[0].name == 'value'
        ctx.types().isSameType(spec.ports[0].type, box)
        ctx.types().isSameType(spec.outputType, javac.STRING)
        spec.outputNullness == Nullability.NON_NULL
    }

    def 'types the produced value through the demand nullness oracle'() {
        when:
        def specs = new FieldPathResolver().descend(Demands.descend(box, 'value', Nullability.NULLABLE), ctx).toList()

        then:
        specs.size() == 1
        specs[0].outputNullness == Nullability.NULLABLE
    }

    def 'rejects private fields'() {
        expect:
        new FieldPathResolver().descend(Demands.descend(box, 'secret'), ctx).toList().empty
    }

    def 'rejects static fields'() {
        expect:
        new FieldPathResolver().descend(Demands.descend(box, 'DEFAULT'), ctx).toList().empty
    }
}
