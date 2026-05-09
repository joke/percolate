package io.github.joke.percolate.processor.spi

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.spi.builtins.ConstructorCall
import io.github.joke.percolate.processor.spi.builtins.DirectAssign
import io.github.joke.percolate.processor.spi.builtins.GetterRead
import io.github.joke.percolate.processor.graph.IncomingValues
import io.github.joke.percolate.processor.graph.Weights
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.*
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

import static javax.lang.model.type.TypeKind.BOOLEAN

@Tag('unit')
class SpiTypesSpec extends Specification {

    def 'GetterRead finds public getter'() {
        given:
        def ctx = mockContextWithGetter('Person', 'getLastName')

        when:
        def steps = new GetterRead().stepsFrom(ctx.typeMirror, "lastName", ctx).toList()

        then:
        steps.size() == 1
        steps[0].weight == Weights.STEP
    }

    def 'GetterRead codegen renders var.method()'() {
        given:
        def ctx = mockContextWithGetter('Person', 'getLastName')
        def steps = new GetterRead().stepsFrom(ctx.typeMirror, "lastName", ctx).toList()
        def inputs = Mock(IncomingValues)
        inputs.single() >> CodeBlock.of('$L', 'personVar')

        when:
        def result = steps[0].codegen.render(null, inputs).toString()

        then:
        result == 'personVar.getLastName()'
    }

    def 'GetterRead finds boolean is-getter'() {
        given:
        def ctx = mockContextWithGetter('Foo', 'isActive', BOOLEAN)

        when:
        def steps = new GetterRead().stepsFrom(ctx.typeMirror, "active", ctx).toList()

        then:
        steps.size() == 1
    }

    def 'GetterRead finds Boolean wrapper is-getter'() {
        given:
        def ctx = mockContextWithBooleanWrapperGetter('Foo', 'isEnabled')

        when:
        def steps = new GetterRead().stepsFrom(ctx.typeMirror, "enabled", ctx).toList()

        then:
        steps.size() == 1
    }

    def 'GetterRead returns empty for missing accessor'() {
        given:
        def ctx = mockContextWithNoMembers('Empty')

        when:
        def steps = new GetterRead().stepsFrom(ctx.typeMirror, "nonexistent", ctx).toList()

        then:
        steps.isEmpty()
    }

    def 'GetterRead excludes getClass from Object'() {
        given:
        def objectElement = Mock(TypeElement)
        objectElement.qualifiedName >> mockName("java.lang.Object")
        def getClassMethod = Mock(ExecutableElement)
        getClassMethod.simpleName >> mockName("getClass")
        getClassMethod.kind >> ElementKind.METHOD
        getClassMethod.parameters >> []
        getClassMethod.enclosingElement >> objectElement
        def stringType = Mock(DeclaredType)
        stringType.kind >> TypeKind.DECLARED
        getClassMethod.returnType >> stringType
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName("test.Person")
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.kind >> TypeKind.DECLARED
        def types = Mock(Types)
        types.asElement(typeMirror) >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [getClassMethod]
        def ctx = new TestResolveCtx(typeMirror, elements, types)

        when:
        def steps = new GetterRead().stepsFrom(typeMirror, "class", ctx).toList()

        then:
        steps.isEmpty()
    }

    def 'ConstructorCall matches exact-name constructor'() {
        given:
        def ctx = mockContextWithCtor('Human', ['firstName', 'lastName'])

        when:
        def result = new ConstructorCall().buildFor(ctx.typeMirror, ['firstName', 'lastName'], ctx)

        then:
        result.isPresent()
        def build = result.get()
        build.slots.size() == 2
        build.slots[0].name == 'firstName'
        build.slots[1].name == 'lastName'
    }

    def 'ConstructorCall does not match subset'() {
        given:
        def ctx = mockContextWithCtor('Human', ['firstName', 'lastName'])

        when:
        def result = new ConstructorCall().buildFor(ctx.typeMirror, ['firstName', 'lastName', 'age'], ctx)

        then:
        !result.isPresent()
    }

    def 'ConstructorCall does not match superset'() {
        given:
        def ctx = mockContextWithCtor('Human', ['firstName', 'lastName'])

        when:
        def result = new ConstructorCall().buildFor(ctx.typeMirror, ['firstName'], ctx)

        then:
        !result.isPresent()
    }

    def 'ConstructorCall ignores targetTails order'() {
        given:
        def ctx = mockContextWithCtor('Human', ['firstName', 'lastName'])

        when:
        def result = new ConstructorCall().buildFor(ctx.typeMirror, ['lastName', 'firstName'], ctx)

        then:
        result.isPresent()
        def build = result.get()
        build.slots[0].name == 'firstName'
        build.slots[1].name == 'lastName'
    }

    def 'DirectAssign matches identical types'() {
        given:
        def types = Mock(Types)
        def typeMirror = Mock(TypeMirror)
        types.isSameType(typeMirror, typeMirror) >> true
        def ctx = new FakeResolveCtx(null, types)

        when:
        def result = new DirectAssign().bridge(typeMirror, typeMirror, ctx).collect(java.util.stream.Collectors.toList())

        then:
        result.size() == 1
        result.get(0).weight == Weights.NOOP
    }

    def 'DirectAssign codegen renders identity — passes input through unchanged'() {
        given:
        def types = Mock(Types)
        def typeMirror = Mock(TypeMirror)
        types.isSameType(typeMirror, typeMirror) >> true
        def ctx = new FakeResolveCtx(null, types)
        def result = new DirectAssign().bridge(typeMirror, typeMirror, ctx).collect(java.util.stream.Collectors.toList())
        def inputs = Mock(IncomingValues)
        inputs.single() >> CodeBlock.of('$L', 'myVar')

        when:
        def codeBlock = result.get(0).codegen.render(null, inputs).toString()

        then:
        codeBlock == 'myVar'
    }

    def 'DirectAssign rejects different types'() {
        given:
        def types = Mock(Types)
        def typeMirror1 = Mock(TypeMirror)
        def typeMirror2 = Mock(TypeMirror)
        types.isSameType(typeMirror1, typeMirror2) >> false
        def ctx = new FakeResolveCtx(null, types)

        when:
        def result = new DirectAssign().bridge(typeMirror1, typeMirror2, ctx).collect(java.util.stream.Collectors.toList())

        then:
        result.isEmpty()
    }

    def 'DirectAssign uses isSameType not isAssignable'() {
        given:
        def types = Mock(Types)
        def listType = Mock(DeclaredType)
        def collectionType = Mock(DeclaredType)
        types.isSameType(listType, collectionType) >> false
        def ctx = new FakeResolveCtx(null, types)

        when:
        def result = new DirectAssign().bridge(listType, collectionType, ctx).collect(java.util.stream.Collectors.toList())

        then:
        result.isEmpty()
    }

    private def mockContextWithGetter(String className, String methodName, TypeKind returnTypeKind = TypeKind.CHAR) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName("test.$className")
        def method = Mock(ExecutableElement)
        method.simpleName >> mockName(methodName)
        method.kind >> ElementKind.METHOD
        method.parameters >> []
        method.enclosingElement >> typeElement
        def returnType = Mock(TypeMirror)
        returnType.getKind() >> returnTypeKind
        method.returnType >> returnType
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.getKind() >> TypeKind.DECLARED
        def types = Mock(Types)
        types.asElement(typeMirror) >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        new TestResolveCtx(typeMirror, elements, types)
    }

    private def mockContextWithBooleanWrapperGetter(String className, String methodName) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName("test.$className")
        def booleanWrapperElement = Mock(TypeElement)
        booleanWrapperElement.qualifiedName >> mockName("java.lang.Boolean")
        def returnType = Mock(DeclaredType)
        returnType.getKind() >> TypeKind.DECLARED
        def method = Mock(ExecutableElement)
        method.simpleName >> mockName(methodName)
        method.kind >> ElementKind.METHOD
        method.parameters >> []
        method.enclosingElement >> typeElement
        method.returnType >> returnType
        def types = Mock(Types)
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.getKind() >> TypeKind.DECLARED
        types.asElement(typeMirror) >> typeElement
        types.asElement(returnType) >> booleanWrapperElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> [method]
        new TestResolveCtx(typeMirror, elements, types)
    }

    private def mockContextWithNoMembers(String className) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName("test.$className")
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.getKind() >> TypeKind.DECLARED
        def types = Mock(Types)
        types.asElement(typeMirror) >> typeElement
        def elements = Mock(Elements)
        elements.getAllMembers(typeElement) >> []
        new TestResolveCtx(typeMirror, elements, types)
    }

    private def mockContextWithCtor(String className, List<String> paramNames) {
        def typeElement = Mock(TypeElement)
        typeElement.qualifiedName >> mockName("test.$className")
        def params = []
        for (def paramName : paramNames) {
            def param = Mock(VariableElement)
            param.simpleName >> mockName(paramName)
            param.kind >> ElementKind.PARAMETER
            def paramType = Mock(DeclaredType)
            def paramTypeElement = Mock(TypeElement)
            paramTypeElement.qualifiedName >> mockName("java.lang.String")
            paramType.asElement() >> paramTypeElement
            paramType.getKind() >> TypeKind.DECLARED
            param.asType() >> paramType
            params << param
        }
        def ctor = Mock(ExecutableElement)
        ctor.kind >> ElementKind.CONSTRUCTOR
        ctor.parameters >> params
        typeElement.getEnclosedElements() >> [ctor]
        def typeMirror = Mock(DeclaredType)
        typeMirror.asElement() >> typeElement
        typeMirror.getKind() >> TypeKind.DECLARED
        def types = Mock(Types)
        types.asElement(typeMirror) >> typeElement
        new TestResolveCtx(typeMirror, null, types)
    }

    private Name mockName(String value) {
        def name = Mock(Name)
        name.toString() >> value
        name.contentEquals(value) >> true
        name.contentEquals(_) >> false
        name
    }

    private static class TestResolveCtx implements ResolveCtx {
        private final TypeMirror typeMirror
        private final Elements elements
        private final Types types

        TestResolveCtx(final TypeMirror typeMirror, final Elements elements, final Types types) {
            this.typeMirror = typeMirror
            this.elements = elements
            this.types = types
        }

        @Override
        public Types types() {
            return types
        }

        @Override
        public Elements elements() {
            return elements
        }

        @Override
        public TypeElement mapperType() {
            return null
        }

        @Override
        public ExecutableElement currentMethod() {
            return null
        }

        @Override
        public CallableMethods callableMethods() {
            return null
        }

        TypeMirror typeMirror() {
            return typeMirror
        }
    }
}
