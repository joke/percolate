package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.Bind
import io.github.joke.percolate.processor.model.MethodDirectives
import io.github.joke.percolate.spi.Subjects
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link ValidateSourceParametersStage} seam, unit-tested directly: a binding whose source path's first segment
 * does not name a scope input of the method (a parameter's own simple name, since no reader published a
 * {@code scopeInput} override in these specs) is reported as a permanent diagnostic (design D14, at the binding's
 * own {@link io.github.joke.percolate.spi.Subject}) — the engine's own rule, read-only over {@code MethodDirectives}.
 */
@Tag('unit')
class ValidateSourceParametersStageSpec extends Specification {

    @Subject
    def stage = new ValidateSourceParametersStage()

    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def 'a source whose first segment names a known parameter produces no diagnostic'() {
        when:
        stage.validate(directives(methodWith('in'), sourceBind('in.name')), ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a source naming an unknown parameter is diagnosed at the binding subject'() {
        when:
        stage.validate(directives(methodWith('in'), sourceBind('bogus.name')), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("unknown scope input 'bogus'")
        }
    }

    def 'a single-segment source is validated as the whole parameter name'() {
        when:
        stage.validate(directives(methodWith('in'), sourceBind('in')), ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a sourceless (constant) binding produces no diagnostic'() {
        when:
        stage.validate(directives(methodWith('in'), new Bind(['status'], [], Subjects.none())), ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'run is a no-op when the context has no method directives'() {
        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'run validates every method installed on the context'() {
        given:
        ctx.methodDirectives = [directives(methodWith('in'), sourceBind('bogus.name'))]

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
    }

    def 'the diagnostic names the method signature with the simple parameter type name'() {
        given:
        def method = methodWithParam(typedParam('in', declared('Person')))

        when:
        stage.validate(directives(method, sourceBind('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('on map(Person)')
    }

    def 'a type-variable parameter renders by its own toString in the signature'() {
        given:
        def method = methodWithParam(typedParam('in', Mock(TypeMirror) { getKind() >> TypeKind.TYPEVAR; toString() >> 'T' }))

        when:
        stage.validate(directives(method, sourceBind('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('map(T)')
    }

    def 'a parameter with no resolvable type renders as a question mark'() {
        given:
        def method = methodWithParam(typedParam('in', null))

        when:
        stage.validate(directives(method, sourceBind('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('map(?)')
    }

    private static Bind sourceBind(final String source) {
        new Bind(['name'], source.split('\\.').toList(), Subjects.none())
    }

    private ExecutableElement methodWith(final String... paramNames) {
        Mock(ExecutableElement) {
            getParameters() >> paramNames.collect { param(it) }
            getSimpleName() >> name('map')
        }
    }

    private ExecutableElement methodWithParam(final VariableElement... parameters) {
        Mock(ExecutableElement) {
            getParameters() >> (parameters as List)
            getSimpleName() >> name('map')
        }
    }

    private VariableElement typedParam(final String paramName, final TypeMirror type) {
        Mock(VariableElement) {
            getSimpleName() >> name(paramName)
            asType() >> type
        }
    }

    private DeclaredType declared(final String simpleName) {
        Mock(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            asElement() >> Mock(TypeElement) { getSimpleName() >> name(simpleName) }
        }
    }

    private VariableElement param(final String paramName) {
        Mock(VariableElement) {
            getSimpleName() >> name(paramName)
            asType() >> Mock(TypeMirror)
        }
    }

    private Name name(final String value) {
        Stub(Name) {
            toString() >> value
        }
    }

    private MethodDirectives directives(final ExecutableElement method, final Bind... binds) {
        new MethodDirectives(method, binds as List, [:], [], [:])
    }
}
