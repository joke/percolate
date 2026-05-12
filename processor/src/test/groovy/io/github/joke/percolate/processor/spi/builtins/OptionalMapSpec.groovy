package io.github.joke.percolate.processor.spi.builtins

import io.github.joke.percolate.processor.spi.ElementSeed
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
class OptionalMapSpec extends Specification {

    def 'emits for Optional to Optional'() {
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

        def innerFrom = Mock(TypeMirror)
        def innerTo = Mock(TypeMirror)
        def from = Mock(DeclaredType)
        from.kind >> TypeKind.DECLARED
        from.getTypeArguments() >> [innerFrom]
        def to = Mock(DeclaredType)
        to.kind >> TypeKind.DECLARED
        to.getTypeArguments() >> [innerTo]

        when:
        def steps = new OptionalMap().bridge(from, to, ctx).toList()

        then:
        steps.size() == 1
        def step = steps[0]
        step.inputType == from
        step.outputType == to
        step.weight == Weights.CONTAINER
        step.elementSeeds.size() == 1
        def seed = step.elementSeeds[0]
        seed.role == 'element'
        seed.inputType == innerFrom
        seed.outputType == innerTo
    }

    def 'codegen throws UnsupportedOperationException'() {
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

        def from = Mock(DeclaredType)
        from.kind >> TypeKind.DECLARED
        from.getTypeArguments() >> [Mock(TypeMirror)]
        def to = Mock(DeclaredType)
        to.kind >> TypeKind.DECLARED
        to.getTypeArguments() >> [Mock(TypeMirror)]

        when:
        def step = new OptionalMap().bridge(from, to, ctx).toList()[0]
        step.codegen.render(null, null)

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message.contains('codegen')
    }

    def 'declines mixed source or target'() {
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
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        def nonOptional = Mock(DeclaredType)
        nonOptional.kind >> TypeKind.DECLARED
        types.isSameType(_, _) >> false

        when:
        def steps1 = new OptionalMap().bridge(nonOptional, nonOptional, ctx).toList()

        then:
        steps1.isEmpty()
    }
}
