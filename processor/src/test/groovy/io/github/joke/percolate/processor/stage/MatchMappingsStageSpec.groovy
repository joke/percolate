package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.MapOptKey
import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.model.MapDirective
import io.github.joke.percolate.processor.model.MapperModel
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.EnumSet

@Tag('unit')
class MatchMappingsStageSpec extends Specification {

    final Elements elements = Mock()
    final Types types = Mock()
    final MatchMappingsStage stage = new MatchMappingsStage(elements, types)

    // -------------------------------------------------------------------------
    // 3.2 — explicit @Map → EXPLICIT_MAP
    // -------------------------------------------------------------------------

    def 'explicit @Map directive produces EXPLICIT_MAP assignment'() {
        given:
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('a', 'b', '', [:])])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        final assignments = result.value().methods[0].assignments
        assignments.size() == 1
        assignments[0].sourcePath == ['a']
        assignments[0].targetName == 'b'
        assignments[0].origin == AssignmentOrigin.EXPLICIT_MAP
        assignments[0].using == null
    }

    def 'explicit @Map with dotted source produces multi-segment sourcePath'() {
        given:
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('customer.name', 'customerName', '', [:])])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        result.value().methods[0].assignments[0].sourcePath == ['customer', 'name']
    }

    // -------------------------------------------------------------------------
    // 3.2 — @Map(using=...) → USING_ROUTED
    // -------------------------------------------------------------------------

    def '@Map(using=...) produces USING_ROUTED assignment'() {
        given:
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('raw', 'normalised', 'normalise', [:])])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        final assgn = result.value().methods[0].assignments[0]
        assgn.origin == AssignmentOrigin.USING_ROUTED
        assgn.using  == 'normalise'
    }

    // -------------------------------------------------------------------------
    // 3.2 — empty using normalisation
    // -------------------------------------------------------------------------

    def 'empty using string is normalised to null in EXPLICIT_MAP assignment'() {
        given:
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('a', 'b', '', [:])])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        result.value().methods[0].assignments[0].using == null
    }

    // -------------------------------------------------------------------------
    // 3.2 — @MapOpt options collected into MappingAssignment.options
    // -------------------------------------------------------------------------

    def '@MapOpt values are collected into the options map'() {
        given:
        final opts   = [(MapOptKey.DATE_FORMAT): 'yyyy-MM-dd']
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('date', 'formattedDate', '', opts)])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        result.value().methods[0].assignments[0].options == [(MapOptKey.DATE_FORMAT): 'yyyy-MM-dd']
    }

    // -------------------------------------------------------------------------
    // 3.2 — auto-mapping fills gaps → AUTO_MAPPED
    // -------------------------------------------------------------------------

    def 'auto-mapping fills unmatched target property with same-named source property'() {
        given:
        final sourceType = declaredType('age')
        final targetType = declaredType('age')
        final method = methodModel('src', sourceType, targetType, [])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        final assignments = result.value().methods[0].assignments
        assignments.size() == 1
        assignments[0].sourcePath == ['age']
        assignments[0].targetName == 'age'
        assignments[0].origin == AssignmentOrigin.AUTO_MAPPED
        assignments[0].using == null
    }

    def 'explicit directive prevents auto-mapping of already-claimed target'() {
        given:
        final sourceType = declaredType('name')
        final targetType = declaredType('name')
        final directive  = new MapDirective('fullName', 'name', '', [:])
        final method = methodModel('src', sourceType, targetType, [directive])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        final assignments = result.value().methods[0].assignments
        // Only the explicit directive — auto-mapping of 'name' is suppressed
        assignments.size() == 1
        assignments[0].origin == AssignmentOrigin.EXPLICIT_MAP
        assignments[0].sourcePath == ['fullName']
        assignments[0].targetName == 'name'
    }

    def 'explicit-before-auto ordering is preserved'() {
        given:
        final sourceType = declaredType('age')
        final targetType = declaredType('givenName', 'age')
        final directive  = new MapDirective('firstName', 'givenName', '', [:])
        final method = methodModel('src', sourceType, targetType, [directive])
        final model  = mapperModel([method])

        when:
        final result = stage.execute(model)

        then:
        result.isSuccess()
        final assignments = result.value().methods[0].assignments
        assignments[0].origin == AssignmentOrigin.EXPLICIT_MAP
        assignments[0].targetName == 'givenName'
        assignments[1].origin == AssignmentOrigin.AUTO_MAPPED
        assignments[1].targetName == 'age'
    }

    // -------------------------------------------------------------------------
    // 3.3 — stage does NOT call property discovery SPIs (name-level only)
    // -------------------------------------------------------------------------

    def 'stage does not interact with Elements or Types for name-only matching'() {
        given:
        final method = methodModel('src', emptyType(), emptyType(), [new MapDirective('a', 'b', '', [:])])
        final model  = mapperModel([method])

        when:
        stage.execute(model)

        then:
        0 * elements._
        0 * types._
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MapperModel mapperModel(final List<MappingMethodModel> methods) {
        return new MapperModel(Mock(TypeElement), methods)
    }

    private MappingMethodModel methodModel(
            final String paramName,
            final TypeMirror sourceType,
            final TypeMirror targetType,
            final List<MapDirective> directives) {
        return new MappingMethodModel(methodElement(paramName), sourceType, targetType, directives)
    }

    private ExecutableElement methodElement(final String paramName) {
        final param = Stub(VariableElement) {
            getSimpleName() >> Stub(Name) { toString() >> paramName }
        }
        return Stub(ExecutableElement) {
            getParameters() >> [param]
        }
    }

    private TypeMirror emptyType() {
        final te = Stub(TypeElement) { getEnclosedElements() >> [] }
        return Stub(DeclaredType) { asElement() >> te }
    }

    private TypeMirror declaredType(final String... propertyNames) {
        final members = propertyNames.collect { name -> getterElement(name) }
        final te = Stub(TypeElement) { getEnclosedElements() >> members }
        return Stub(DeclaredType) { asElement() >> te }
    }

    private ExecutableElement getterElement(final String propertyName) {
        final methodName = 'get' + propertyName.capitalize()
        Stub(ExecutableElement) {
            getKind() >> ElementKind.METHOD
            getSimpleName() >> Stub(Name) { toString() >> methodName }
            getModifiers() >> EnumSet.of(Modifier.PUBLIC)
            getParameters() >> []
            getReturnType() >> Stub(TypeMirror) { getKind() >> TypeKind.DECLARED }
        }
    }
}
