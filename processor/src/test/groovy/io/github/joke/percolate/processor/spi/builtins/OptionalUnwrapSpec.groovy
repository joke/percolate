package io.github.joke.percolate.processor.spi.builtins

import io.github.joke.percolate.processor.graph.IncomingValues
import io.github.joke.percolate.processor.spi.ResolveCtx
import io.github.joke.percolate.processor.spi.Weights
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class OptionalUnwrapSpec extends Specification {

    def 'emits for Optional source'() {
        given:
        def types = Mock(Types)
        def elements = Mock(Elements)
        def optionalTypeElement = Mock(TypeElement)
        def optionalDeclaredType = Mock(DeclaredType)
        optionalDeclaredType.kind >> TypeKind.DECLARED
        optionalTypeElement.asType() >> optionalDeclaredType
        def erasedOptional = Mock(DeclaredType)
        erasedOptional.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.Optional') >> optionalTypeElement
        types.erasure(_) >> erasedOptional
        types.isSameType(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        def innerType = Mock(TypeMirror)
        def from = Mock(DeclaredType)
        from.kind >> TypeKind.DECLARED
        from.getTypeArguments() >> [innerType]
        def to = Mock(TypeMirror)
        to.kind >> TypeKind.DECLARED

        when:
        def steps = new OptionalUnwrap().bridge(from, to, ctx).toList()

        then:
        steps.size() == 1
        def step = steps[0]
        step.inputType == from
        step.outputType == innerType
        step.weight == Weights.CONTAINER
        step.elementSeeds.isEmpty()
    }

    def 'declines non-Optional source'() {
        given:
        def types = Mock(Types)
        def elements = Mock(Elements)
        def optionalTypeElement = Mock(TypeElement)
        def optionalDeclaredType = Mock(DeclaredType)
        optionalDeclaredType.kind >> TypeKind.DECLARED
        optionalTypeElement.asType() >> optionalDeclaredType
        def erasedOptional = Mock(DeclaredType)
        erasedOptional.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.Optional') >> optionalTypeElement
        types.erasure(_) >> erasedOptional
        types.isSameType(_, _) >> false
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        def from = Mock(DeclaredType)
        from.kind >> TypeKind.DECLARED
        def to = Mock(TypeMirror)
        to.kind >> TypeKind.DECLARED

        when:
        def steps = new OptionalUnwrap().bridge(from, to, ctx).toList()

        then:
        steps.isEmpty()
    }
}
