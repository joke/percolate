package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

/**
 * {@link ValidateSourceParametersStage} seam, unit-tested directly against a mock {@link Messager}: a directive whose
 * source's first segment does not name a method parameter is diagnosed (at the {@code source} value) and dropped; a
 * matching source, a single-segment source, and a sourceless (constant) directive all survive untouched.
 */
@Tag('unit')
class ValidateSourceParametersStageSpec extends Specification {

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateSourceParametersStage(diagnostics)

    def mirror = Mock(AnnotationMirror)
    def sourceValue = Mock(AnnotationValue)

    def 'a source whose first segment names a known parameter is kept, no diagnostic'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('in.name')))

        then:
        0 * messager.printMessage(*_)
        result.methods[0].directives.size() == 1
    }

    def 'a source naming an unknown parameter is diagnosed at the source value and dropped'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('bogus.name')))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("unknown source parameter 'bogus'") }, _, mirror,
                sourceValue)
        result.methods[0].directives.empty
    }

    def 'a single-segment source is validated as the whole parameter name'() {
        when:
        def result = stage.validate(mappings(methodWith('in'), sourceDirective('in')))

        then:
        0 * messager.printMessage(*_)
        result.methods[0].directives.size() == 1
    }

    def 'a sourceless (constant) directive is kept without parameter validation'() {
        given:
        def constant = new MappingDirective('status', null, 'ACTIVE', null, null, null, mirror, Mock(AnnotationValue), null,
                Mock(AnnotationValue), null, null, null)

        when:
        def result = stage.validate(mappings(methodWith('in'), constant))

        then:
        0 * messager.printMessage(*_)
        result.methods[0].directives.size() == 1
    }

    def 'run is a no-op when the context has no mappings'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)

        expect:
        ctx.mappings == null
    }

    def 'run installs the validated mappings, dropping the unknown-parameter directive'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.mappings = mappings(methodWith('in'), sourceDirective('bogus.name'))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, _, _, mirror, sourceValue)

        expect:
        ctx.mappings.methods[0].directives.empty
    }

    def 'the diagnostic names the method signature with the simple parameter type name'() {
        given:
        def method = methodWith(typedParam('in', declared('Person')))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('in @Map on map(Person)') }, method, mirror,
                sourceValue)
    }

    def 'a type-variable parameter renders by its own toString in the signature'() {
        given:
        def method = methodWith(typedParam('in', Mock(TypeMirror) { getKind() >> TypeKind.TYPEVAR; toString() >> 'T' }))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('map(T)') }, method, mirror, sourceValue)
    }

    def 'a parameter with no resolvable type renders as a question mark'() {
        given:
        def method = methodWith(typedParam('in', null))

        when:
        stage.validate(mappings(method, sourceDirective('bogus')))

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('map(?)') }, method, mirror, sourceValue)
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
