package io.github.joke.percolate.spi.builtins

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Containers
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ElementScope
import io.github.joke.percolate.spi.Intent
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.WrapperCodegen
import io.github.joke.percolate.spi.builtins.test.Renders
import io.github.joke.percolate.spi.builtins.test.ResolveCtxBuilder
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class OptionalContainerSpec extends Specification {

    @Shared ResolveCtx ctx = new ResolveCtxBuilder().build()
    @Shared TypeMirror optionalOfString

    def setupSpec() {
        ['java.util.Optional'].each { TypeUniverse.elements().getTypeElement(it) }
        optionalOfString = TypeUniverse.types().getDeclaredType(
                TypeUniverse.elements().getTypeElement('java.util.Optional'), TypeUniverse.STRING)
        Containers.isOptional(optionalOfString, ctx)
    }

    def 'scalar target synthesises an ENTERING unwrap from Optional<target>, regardless of the source side'() {
        when:
        // The from side is ignored: a wrapper unwrap is offered for any scalar target by synthesising
        // Optional<target> as its input (so a wrapped source can be reached even before a wrapper node exists).
        def steps = new OptionalContainer().bridge(TypeUniverse.INTEGER, TypeUniverse.STRING, ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        steps[0].scope.orElse(null) == ElementScope.ENTERING
        ctx.types().isSameType(steps[0].inputs[0].type, optionalOfString)
        ctx.types().isSameType(steps[0].output, TypeUniverse.STRING)
        steps[0].codegen instanceof WrapperCodegen
    }

    def 'Optional<E> target emits only a scopeless ofNullable wrap (no collect — wrappers never collect)'() {
        when:
        def steps = new OptionalContainer().bridge(TypeUniverse.STRING, optionalOfString, ctx).toList()

        then:
        steps.size() == 1
        steps[0].intent == Intent.BOUNDARY
        steps[0].scope.empty
        steps[0].codegen instanceof EdgeCodegen
        Renders.edge(steps[0].codegen, 'x') == 'java.util.Optional.ofNullable(x)'
        steps.every { it.scope.orElse(null) != ElementScope.EXITING }
    }

    def 'presence snippets render the Optional paradigm'() {
        given:
        def c = new OptionalContainer()

        expect:
        c.iterate(CodeBlock.of('o')).toString() == 'o.stream()'
        c.wrap(CodeBlock.of('x')).toString() == 'java.util.Optional.ofNullable(x)'
        c.mapPresence(CodeBlock.of('o'), 'v', CodeBlock.of('f(v)')).toString() == 'o.map(v -> f(v))'
        c.unwrap(CodeBlock.of('o'), Nullability.NON_NULL).toString() == 'o.orElseThrow()'
        c.unwrap(CodeBlock.of('o'), Nullability.NULLABLE).toString() == 'o.orElse(null)'
    }
}
