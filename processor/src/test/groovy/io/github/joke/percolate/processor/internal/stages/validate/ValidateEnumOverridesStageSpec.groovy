package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.EnumOverrideDirective
import io.github.joke.percolate.processor.model.GoalSpec
import io.github.joke.percolate.processor.model.MapperShape
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

/**
 * {@link ValidateEnumOverridesStage} seam, unit-tested directly against a mock {@link Messager}: a method's
 * already-discovered {@code @MapEnum} table (carried on its {@link GoalSpec}) is checked against the actual
 * constants of the return type ({@code target}) and, for a single-parameter method, the parameter type
 * ({@code source}) — an unmatched name is diagnosed at the corresponding {@link AnnotationValue}. A method whose
 * return type is not an {@code enum}, or whose single parameter is not an {@code enum}, is skipped for that side.
 */
@Tag('unit')
class ValidateEnumOverridesStageSpec extends Specification {

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateEnumOverridesStage(diagnostics)

    def mirror = Mock(AnnotationMirror)
    def sourceValue = Mock(AnnotationValue)
    def targetValue = Mock(AnnotationValue)

    def 'a valid override naming a real source and target constant is not diagnosed'() {
        def method = methodWith(enumType('MyStatus', 'NEW', 'DONE'), enumType('OrderStatus', 'CREATED', 'DONE'))
        def ctx = ctxWith(method, override('NEW', 'CREATED'))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'an unknown target constant is diagnosed at the target value'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), enumType('OrderStatus', 'CREATED'))
        def ctx = ctxWith(method, override('NEW', 'MISSING'))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("unknown target constant 'MISSING'") }, method,
                mirror, targetValue)
        0 * messager.printMessage(*_)
    }

    def 'an unknown source constant is diagnosed at the source value'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), enumType('OrderStatus', 'CREATED'))
        def ctx = ctxWith(method, override('BOGUS', 'CREATED'))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("unknown source constant 'BOGUS'") }, method,
                mirror, sourceValue)
        0 * messager.printMessage(*_)
    }

    def 'both an unknown source and target are each diagnosed once'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), enumType('OrderStatus', 'CREATED'))
        def ctx = ctxWith(method, override('BOGUS', 'MISSING'))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('unknown source') }, method, mirror, sourceValue)
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('unknown target') }, method, mirror, targetValue)
        0 * messager.printMessage(*_)
    }

    def 'a non-enum return type skips the target check'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), Mock(DeclaredType) { getKind() >> TypeKind.DECLARED
            asElement() >> Mock(TypeElement) { getKind() >> ElementKind.CLASS }
        })
        def ctx = ctxWith(method, override('NEW', 'ANYTHING'))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a non-single-parameter method skips the source check'() {
        def method = Mock(ExecutableElement) {
            getParameters() >> []
            getReturnType() >> enumType('OrderStatus', 'CREATED')
        }
        def ctx = ctxWith(method, override('ANYTHING', 'CREATED'))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a non-enum single parameter skips the source check'() {
        def nonEnumParam = Mock(DeclaredType) { getKind() >> TypeKind.DECLARED
            asElement() >> Mock(TypeElement) { getKind() >> ElementKind.CLASS }
        }
        def method = Mock(ExecutableElement) {
            getParameters() >> [Mock(VariableElement) { asType() >> nonEnumParam }]
            getReturnType() >> enumType('OrderStatus', 'CREATED')
        }
        def ctx = ctxWith(method, override('ANYTHING', 'CREATED'))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a method with no enum overrides is not checked at all'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), enumType('OrderStatus', 'CREATED'))
        def ctx = ctxWith(method)

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a method with no goal spec entry is skipped'() {
        def method = methodWith(enumType('MyStatus', 'NEW'), enumType('OrderStatus', 'CREATED'))
        def mapperType = Mock(TypeElement)
        def ctx = new MapperContext(mapperType)
        ctx.shape = new MapperShape(mapperType, [method])

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'run is a no-op when discovery produced no shape'() {
        def ctx = new MapperContext(Mock(TypeElement))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    private MapperContext ctxWith(final ExecutableElement method, final EnumOverrideDirective... overrides) {
        def mapperType = Mock(TypeElement)
        def ctx = new MapperContext(mapperType)
        ctx.shape = new MapperShape(mapperType, [method])
        ctx.goalSpecs[new MethodScope(method)] = GoalSpec.from([], overrides as List)
        ctx
    }

    private EnumOverrideDirective override(final String source, final String target) {
        new EnumOverrideDirective(source, target, mirror, sourceValue, targetValue)
    }

    private ExecutableElement methodWith(final TypeMirror paramType, final TypeMirror returnType) {
        Mock(ExecutableElement) {
            getParameters() >> [Mock(VariableElement) { asType() >> paramType }]
            getReturnType() >> returnType
        }
    }

    private TypeMirror enumType(final String simpleName, final String... constants) {
        def members = constants.collect { constantName ->
            Mock(Element) {
                getKind() >> ElementKind.ENUM_CONSTANT
                getSimpleName() >> name(constantName)
            }
        }
        Mock(DeclaredType) {
            getKind() >> TypeKind.DECLARED
            asElement() >> Mock(TypeElement) {
                getKind() >> ElementKind.ENUM
                getSimpleName() >> name(simpleName)
                getEnclosedElements() >> members
            }
        }
    }

    private Name name(final String value) {
        Stub(Name) {
            toString() >> value
        }
    }
}
