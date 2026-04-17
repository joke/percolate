package io.github.joke.percolate.processor.stage

import io.github.joke.percolate.processor.match.AssignmentOrigin
import io.github.joke.percolate.processor.match.MatchedModel
import io.github.joke.percolate.processor.match.MappingAssignment
import io.github.joke.percolate.processor.match.MethodMatching
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

@Tag('unit')
class ValidateMatchingStageSpec extends Specification {

    final ValidateMatchingStage stage = new ValidateMatchingStage()

    // -------------------------------------------------------------------------
    // 4.2 — duplicate-target detection
    // -------------------------------------------------------------------------

    def 'duplicate @Map directives targeting the same slot produce a diagnostic'() {
        given:
        final mapperType = mapperTypeWithMethods([])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        final a1 = MappingAssignment.of(['firstName'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final a2 = MappingAssignment.of(['lastName'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = new MethodMatching(method, model, [a1, a2])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        !result.isSuccess()
        result.errors().size() == 1
        result.errors()[0].message.contains('name')
        result.errors()[0].element.is(method)
    }

    def 'no duplicate targets passes validation'() {
        given:
        final mapperType = mapperTypeWithMethods([])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        final a1 = MappingAssignment.of(['firstName'], 'givenName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final a2 = MappingAssignment.of(['lastName'], 'familyName', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = new MethodMatching(method, model, [a1, a2])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        result.isSuccess()
    }

    // -------------------------------------------------------------------------
    // 4.3 — unknown source-root parameter (multi-param methods)
    // -------------------------------------------------------------------------

    def 'unknown source-root parameter on multi-param method produces diagnostic'() {
        given:
        final mapperType = mapperTypeWithMethods([])
        final method     = executableElement('map', 2, 'orderParam', 'ctxParam')
        final model      = Mock(MappingMethodModel)
        final a = MappingAssignment.of(['unknownParam', 'name'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        !result.isSuccess()
        result.errors()[0].message.contains('unknownParam')
    }

    def 'valid source-root parameter on multi-param method passes'() {
        given:
        final mapperType = mapperTypeWithMethods([])
        final method     = executableElement('map', 2, 'order', 'ctx')
        final model      = Mock(MappingMethodModel)
        final a = MappingAssignment.of(['order', 'name'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        result.isSuccess()
    }

    def 'single-param method skips source-root parameter validation'() {
        given:
        final mapperType = mapperTypeWithMethods([])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        // 'anyName' would not match a param name, but check is skipped for single-param
        final a = MappingAssignment.of(['anyName'], 'target', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        result.isSuccess()
    }

    // -------------------------------------------------------------------------
    // 4.4 — unresolved using= method
    // -------------------------------------------------------------------------

    def 'using= referencing non-existent method produces diagnostic'() {
        given:
        final mapperType = mapperTypeWithMethods(['mapAddress'])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        final a = MappingAssignment.of(['raw'], 'normalised', [:], 'normalise', AssignmentOrigin.USING_ROUTED)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        !result.isSuccess()
        result.errors()[0].message.contains('normalise')
        result.errors()[0].element.is(method)
    }

    def 'using= referencing existing method passes'() {
        given:
        final mapperType = mapperTypeWithMethods(['normalise'])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        final a = MappingAssignment.of(['raw'], 'normalised', [:], 'normalise', AssignmentOrigin.USING_ROUTED)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        result.isSuccess()
    }

    def 'unresolved using= includes fuzzy suggestion when close name exists'() {
        given:
        final mapperType = mapperTypeWithMethods(['normalise'])
        final method     = executableElement('map', 1)
        final model      = Mock(MappingMethodModel)
        // 'normaliz' is 1 edit away from 'normalise'
        final a = MappingAssignment.of(['raw'], 'normalised', [:], 'normaliz', AssignmentOrigin.USING_ROUTED)
        final matching   = new MethodMatching(method, model, [a])
        final matched    = new MatchedModel(mapperType, [matching])

        when:
        final result = stage.execute(matched)

        then:
        !result.isSuccess()
        result.errors()[0].message.contains('normalise')
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TypeElement mapperTypeWithMethods(final List<String> methodNames) {
        final methodElements = methodNames.collect { name ->
            Stub(ExecutableElement) {
                getKind() >> ElementKind.METHOD
                getSimpleName() >> Stub(Name) { toString() >> name }
            }
        }
        Stub(TypeElement) {
            getEnclosedElements() >> methodElements
            toString() >> 'test.TestMapper'
        }
    }

    private ExecutableElement executableElement(final String name, final int paramCount,
            final String... paramNames) {
        final params = (0..<paramCount).collect { i ->
            final pname = paramNames.length > i ? paramNames[i] : "param$i"
            Stub(VariableElement) {
                getSimpleName() >> Stub(Name) { toString() >> pname }
            }
        }
        Stub(ExecutableElement) {
            getSimpleName() >> Stub(Name) { toString() >> name }
            getParameters() >> params
        }
    }
}
