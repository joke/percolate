package io.github.joke.percolate.processor.match

import io.github.joke.percolate.MapOptKey
import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

@Tag('unit')
class MatchingModelSpec extends Specification {

    // -------------------------------------------------------------------------
    // AssignmentOrigin
    // -------------------------------------------------------------------------

    def 'AssignmentOrigin has exactly three values'() {
        expect:
        AssignmentOrigin.values().toList() == [AssignmentOrigin.EXPLICIT_MAP, AssignmentOrigin.AUTO_MAPPED, AssignmentOrigin.USING_ROUTED]
    }

    // -------------------------------------------------------------------------
    // MappingAssignment — value equality
    // -------------------------------------------------------------------------

    def 'MappingAssignment equality holds when all fields match'() {
        given:
        final a = MappingAssignment.of(['customer', 'name'], 'customerName', [:], 'someMethod', AssignmentOrigin.EXPLICIT_MAP)
        final b = MappingAssignment.of(['customer', 'name'], 'customerName', [:], 'someMethod', AssignmentOrigin.EXPLICIT_MAP)

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def 'MappingAssignment inequality when origin differs'() {
        given:
        final a = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final b = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)

        expect:
        a != b
    }

    def 'MappingAssignment inequality when sourcePath differs'() {
        given:
        final a = MappingAssignment.of(['address', 'street'], 'street', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final b = MappingAssignment.of(['street'], 'street', [:], null, AssignmentOrigin.EXPLICIT_MAP)

        expect:
        a != b
    }

    // -------------------------------------------------------------------------
    // MappingAssignment — null-safety of using
    // -------------------------------------------------------------------------

    def 'MappingAssignment accepts null using and preserves it'() {
        when:
        final assignment = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)

        then:
        assignment.using == null
    }

    def 'MappingAssignment with non-empty using preserves it'() {
        when:
        final assignment = MappingAssignment.of(['raw'], 'normalised', [:], 'normalise', AssignmentOrigin.USING_ROUTED)

        then:
        assignment.using == 'normalise'
    }

    // -------------------------------------------------------------------------
    // MappingAssignment — normalisation of empty using to null
    // -------------------------------------------------------------------------

    def 'MappingAssignment normalises empty using string to null'() {
        when:
        final assignment = MappingAssignment.of(['name'], 'name', [:], '', AssignmentOrigin.EXPLICIT_MAP)

        then:
        assignment.using == null
    }

    def 'MappingAssignment with null and empty using are equal'() {
        given:
        final withNull  = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.EXPLICIT_MAP)
        final withEmpty = MappingAssignment.of(['name'], 'name', [:], '',   AssignmentOrigin.EXPLICIT_MAP)

        expect:
        withNull == withEmpty
        withNull.hashCode() == withEmpty.hashCode()
    }

    // -------------------------------------------------------------------------
    // MappingAssignment — MapOpt options
    // -------------------------------------------------------------------------

    def 'MappingAssignment preserves options map'() {
        when:
        final assignment = MappingAssignment.of(['date'], 'formattedDate', [(MapOptKey.DATE_FORMAT): 'yyyy-MM-dd'], null, AssignmentOrigin.EXPLICIT_MAP)

        then:
        assignment.options == [(MapOptKey.DATE_FORMAT): 'yyyy-MM-dd']
    }

    // -------------------------------------------------------------------------
    // MethodMatching
    // -------------------------------------------------------------------------

    def 'MethodMatching equality holds for same fields'() {
        given:
        final method = Mock(ExecutableElement)
        final model  = Mock(MappingMethodModel)
        final assgn  = MappingAssignment.of(['name'], 'name', [:], null, AssignmentOrigin.AUTO_MAPPED)

        final a = new MethodMatching(method, model, [assgn])
        final b = new MethodMatching(method, model, [assgn])

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def 'MethodMatching exposes assignments'() {
        given:
        final method  = Mock(ExecutableElement)
        final model   = Mock(MappingMethodModel)
        final assgn   = MappingAssignment.of(['age'], 'age', [:], null, AssignmentOrigin.AUTO_MAPPED)

        when:
        final matching = new MethodMatching(method, model, [assgn])

        then:
        matching.assignments == [assgn]
        matching.method.is(method)
        matching.model.is(model)
    }

    // -------------------------------------------------------------------------
    // MatchedModel
    // -------------------------------------------------------------------------

    def 'MatchedModel exposes mapperType and methods'() {
        given:
        final mapperType = Mock(TypeElement)
        final method     = Mock(ExecutableElement)
        final model      = Mock(MappingMethodModel)
        final matching   = new MethodMatching(method, model, [])

        when:
        final matched = new MatchedModel(mapperType, [matching])

        then:
        matched.mapperType.is(mapperType)
        matched.methods == [matching]
    }

    def 'MatchedModel does not expose graph accessor'() {
        expect:
        !MatchedModel.methods.any { m ->
            m.returnType.name.contains('DirectedGraph') || m.returnType.name.contains('Graph')
        }
    }
}
