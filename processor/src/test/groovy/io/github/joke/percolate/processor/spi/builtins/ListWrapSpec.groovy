package io.github.joke.percolate.processor.spi.builtins

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
class ListWrapSpec extends Specification {

    def 'emits for List target'() {
        given:
        def types = Mock(Types)
        def elements = Mock(Elements)
        def listTypeElement = Mock(TypeElement)
        def listDeclaredType = Mock(DeclaredType)
        listDeclaredType.kind >> TypeKind.DECLARED
        listTypeElement.asType() >> listDeclaredType
        def erasedList = Mock(DeclaredType)
        erasedList.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.List') >> listTypeElement
        types.erasure(_) >> erasedList
        types.isSameType(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        def from = Mock(TypeMirror)
        from.kind >> TypeKind.DECLARED
        def innerType = Mock(TypeMirror)
        def to = Mock(DeclaredType)
        to.kind >> TypeKind.DECLARED
        to.getTypeArguments() >> [innerType]

        when:
        def steps = new ListWrap().bridge(from, to, ctx).toList()

        then:
        steps.size() == 1
        def step = steps[0]
        step.inputType == innerType
        step.outputType == to
        step.weight == Weights.CONTAINER
        step.elementSeeds.isEmpty()
    }

    def 'declines non-List target'() {
        given:
        def types = Mock(Types)
        def elements = Mock(Elements)
        def listTypeElement = Mock(TypeElement)
        def listDeclaredType = Mock(DeclaredType)
        listDeclaredType.kind >> TypeKind.DECLARED
        listTypeElement.asType() >> listDeclaredType
        def erasedList = Mock(DeclaredType)
        erasedList.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.List') >> listTypeElement
        types.erasure(_) >> erasedList
        types.isSameType(_, _) >> false
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements

        def from = Mock(TypeMirror)
        from.kind >> TypeKind.DECLARED
        def to = Mock(DeclaredType)
        to.kind >> TypeKind.DECLARED
        to.getTypeArguments() >> []

        when:
        def steps = new ListWrap().bridge(from, to, ctx).toList()

        then:
        steps.isEmpty()
    }
}
