package io.github.joke.percolate.processor.spi.builtins

import io.github.joke.percolate.processor.spi.ResolveCtx
import io.github.joke.percolate.processor.spi.Weights
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

@Tag('unit')
class ListMapSpec extends Specification {

    def 'emits for List to List'() {
        given:
        def ctx = mockMatchingCtx()
        def innerFrom = Mock(TypeMirror)
        def innerTo = Mock(TypeMirror)
        def from = mockDeclaredType([innerFrom])
        def to = mockDeclaredType([innerTo])

        when:
        def steps = new ListMap().bridge(from, to, ctx).toList()

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

    def 'emits for Set to List'() {
        given:
        def ctx = mockMatchingCtx()
        def innerFrom = Mock(TypeMirror)
        def innerTo = Mock(TypeMirror)
        def from = mockDeclaredType([innerFrom])
        def to = mockDeclaredType([innerTo])

        when:
        def steps = new ListMap().bridge(from, to, ctx).toList()

        then:
        steps.size() == 1
        steps[0].elementSeeds[0].inputType == innerFrom
        steps[0].elementSeeds[0].outputType == innerTo
    }

    def 'emits for array to List'() {
        given:
        def ctx = mockArrayCtx()
        def componentType = Mock(TypeMirror)
        def innerTo = Mock(TypeMirror)
        def from = Mock(ArrayType)
        from.kind >> TypeKind.ARRAY
        from.getComponentType() >> componentType
        def to = mockDeclaredType([innerTo])

        when:
        def steps = new ListMap().bridge(from, to, ctx).toList()

        then:
        steps.size() == 1
        steps[0].elementSeeds[0].inputType == componentType
    }

    def 'codegen throws UnsupportedOperationException'() {
        given:
        def ctx = mockMatchingCtx()
        def from = mockDeclaredType([Mock(TypeMirror)])
        def to = mockDeclaredType([Mock(TypeMirror)])

        when:
        def step = new ListMap().bridge(from, to, ctx).toList()[0]
        step.codegen.render(null, null)

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message.contains('codegen')
    }

    def 'declines non-List target'() {
        given:
        def ctx = mockDecliningCtx()
        def from = mockDeclaredType([Mock(TypeMirror)])
        def to = mockDeclaredType([])

        when:
        def steps = new ListMap().bridge(from, to, ctx).toList()

        then:
        steps.isEmpty()
    }

    private ResolveCtx mockMatchingCtx() {
        def types = Mock(Types)
        def elements = Mock(Elements)
        def listTypeElement = Mock(TypeElement)
        def listDeclaredType = Mock(DeclaredType)
        listDeclaredType.kind >> TypeKind.DECLARED
        listTypeElement.asType() >> listDeclaredType
        def iterableTypeElement = Mock(TypeElement)
        def iterableDeclaredType = Mock(DeclaredType)
        iterableDeclaredType.kind >> TypeKind.DECLARED
        iterableTypeElement.asType() >> iterableDeclaredType
        def erased = Mock(DeclaredType)
        erased.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.List') >> listTypeElement
        elements.getTypeElement('java.lang.Iterable') >> iterableTypeElement
        types.erasure(_) >> erased
        types.isSameType(_, _) >> true
        types.isAssignable(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements
        ctx
    }

    private ResolveCtx mockArrayCtx() {
        def types = Mock(Types)
        def elements = Mock(Elements)
        def listTypeElement = Mock(TypeElement)
        def listDeclaredType = Mock(DeclaredType)
        listDeclaredType.kind >> TypeKind.DECLARED
        listTypeElement.asType() >> listDeclaredType
        def erased = Mock(DeclaredType)
        erased.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.List') >> listTypeElement
        types.erasure(_) >> erased
        types.isSameType(_, _) >> true
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements
        ctx
    }

    private ResolveCtx mockDecliningCtx() {
        def types = Mock(Types)
        def elements = Mock(Elements)
        def listTypeElement = Mock(TypeElement)
        def listDeclaredType = Mock(DeclaredType)
        listDeclaredType.kind >> TypeKind.DECLARED
        listTypeElement.asType() >> listDeclaredType
        def erased = Mock(DeclaredType)
        erased.kind >> TypeKind.DECLARED
        elements.getTypeElement('java.util.List') >> listTypeElement
        types.erasure(_) >> erased
        types.isSameType(_, _) >> false
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types
        ctx.elements() >> elements
        ctx
    }

    private DeclaredType mockDeclaredType(List<TypeMirror> typeArgs) {
        def dt = Mock(DeclaredType)
        dt.kind >> TypeKind.DECLARED
        dt.getTypeArguments() >> typeArgs
        dt
    }
}
