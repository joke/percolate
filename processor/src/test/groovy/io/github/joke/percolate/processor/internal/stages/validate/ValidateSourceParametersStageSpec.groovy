package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link ValidateSourceParametersStage} seam, unit-tested directly: a directive whose source's first segment does
 * not name a method parameter is reported as a permanent diagnostic (design D14, at the {@code source} value) and
 * dropped; a matching source, a single-segment source, and a sourceless (constant) directive all survive untouched.
 */
@Tag('unit')
class ValidateSourceParametersStageSpec extends Specification {

    @Subject
    def stage = new ValidateSourceParametersStage()

    def mirror = Mock(AnnotationMirror)
    def sourceValue = Mock(AnnotationValue)
    def mapperType = Mock(TypeElement)
    def ctx = new MapperContext(mapperType)

    def 'a source whose first segment names a known parameter is kept, no diagnostic'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('in.name')), ctx)

        then:
        ctx.diagnostics.empty
        result.methods[0].directives.size() == 1
    }

    def 'a source naming an unknown parameter is diagnosed at the source value and dropped'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('bogus.name')), ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("unknown source parameter 'bogus'")
        }
        result.methods[0].directives.empty
    }

    def 'a single-segment source is validated as the whole parameter name'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('in')), ctx)

        then:
        ctx.diagnostics.empty
        result.methods[0].directives.size() == 1
    }

    def 'a sourceless (constant) directive is kept without parameter validation'() {
        given:
        def constant = new MappingDirective('status', null, 'ACTIVE', null, null, null, mirror, Mock(AnnotationValue), null,
                Mock(AnnotationValue), null, null, null)

        when:
        def result = stage.validate(mappings(methodWith('in'), constant), ctx)

        then:
        ctx.diagnostics.empty
        result.methods[0].directives.size() == 1
    }

    def 'run is a no-op when the context has no mappings'() {
        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty

        expect:
        ctx.mappings == null
    }

    def 'run installs the validated mappings, dropping the unknown-parameter directive'() {
        given:
        ctx.mappings = mappings(methodWith('in'), sourceDirective('bogus.name'))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1

        expect:
        ctx.mappings.methods[0].directives.empty
    }

    def 'the diagnostic names the method signature with the simple parameter type name'() {
        given:
        def method = methodWith(typedParam('in', declared('Person')))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('in @Map on map(Person)')
    }

    def 'a type-variable parameter renders by its own toString in the signature'() {
        given:
        def method = methodWith(typedParam('in', Mock(TypeMirror) { getKind() >> TypeKind.TYPEVAR; toString() >> 'T' }))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('map(T)')
    }

    def 'a parameter with no resolvable type renders as a question mark'() {
        given:
        def method = methodWith(typedParam('in', null))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')), ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('map(?)')
    }

    private ExecutableElement methodWith(final String... paramNames) {
        Mock(ExecutableElement) {
            getParameters() >> paramNames.collect { param(it) }
            getSimpleName() >> name('map')
        }
    }

    private ExecutableElement methodWith(final VariableElement... parameters) {
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

    private MappingDirective sourceDirective(final String source) {
        new MappingDirective('name', source, null, null, null, null, mirror, Mock(AnnotationValue), sourceValue, null, null, null, null)
    }

    private MapperMappings mappings(final ExecutableElement method, final MappingDirective... directives) {
        new MapperMappings(null, [new MethodMappings(method, directives as List)])
    }
}
