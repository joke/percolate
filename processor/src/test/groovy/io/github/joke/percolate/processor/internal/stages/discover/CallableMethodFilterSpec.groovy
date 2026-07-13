package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.spi.ThisReceiver
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types

import static javax.lang.model.element.ElementKind.CONSTRUCTOR
import static javax.lang.model.element.ElementKind.FIELD
import static javax.lang.model.element.ElementKind.METHOD

/**
 * {@link CallableMethodFilter} (and the {@link IndexCallableMethods} view it builds) unit-tested on plain
 * {@link CandidateDescriptor}s: it keeps the single-parameter, non-{@code Object} methods, and {@code producing}
 * answers with the ones whose return type is assignable to the demand, each carrying the {@link ThisReceiver}.
 * Assignability is the one seam question — a single stub on a mocked {@link Types} — while every return-type/output
 * {@link TypeMirror} and {@link ExecutableElement} stays an opaque, never-stubbed token.
 */
@Tag('unit')
class CallableMethodFilterSpec extends Specification {

    Types types = Mock()
    CallableMethodFilter filter = new CallableMethodFilter(types)

    def 'keeps only single-parameter non-Object methods; producing answers the assignable ones with a this-receiver'() {
        ExecutableElement makeHuman = Mock()
        ExecutableElement noArg = Mock()
        ExecutableElement pair = Mock()
        ExecutableElement equalsObject = Mock()
        ExecutableElement field = Mock()
        TypeMirror humanReturn = Mock()
        TypeMirror irrelevant = Mock()
        TypeMirror output = Mock()
        def descriptors = [
                candidate(METHOD, 1, false, humanReturn, makeHuman),
                candidate(METHOD, 0, false, irrelevant, noArg),
                candidate(METHOD, 2, false, irrelevant, pair),
                candidate(METHOD, 1, true, irrelevant, equalsObject),
                candidate(FIELD, 1, false, irrelevant, field),
        ]

        when:
        def result = filter.filter(descriptors).producing(output).toList()

        then: 'only the single-parameter non-Object method survives the filter and reaches the assignability check'
        1 * types.isAssignable(humanReturn, output) >> true
        0 * _

        expect:
        result*.method == [makeHuman]
        result*.receiver == [ThisReceiver.INSTANCE]
    }

    def 'producing drops a surviving candidate whose return type is not assignable to the demand'() {
        ExecutableElement makeHuman = Mock()
        ExecutableElement describe = Mock()
        TypeMirror humanReturn = Mock()
        TypeMirror stringReturn = Mock()
        TypeMirror output = Mock()
        def descriptors = [
                candidate(METHOD, 1, false, humanReturn, makeHuman),
                candidate(METHOD, 1, false, stringReturn, describe),
        ]

        when:
        def result = filter.filter(descriptors).producing(output).toList()

        then:
        1 * types.isAssignable(humanReturn, output) >> false
        1 * types.isAssignable(stringReturn, output) >> true
        0 * _

        expect:
        result*.method == [describe]
    }

    def 'a duplicate candidate is indexed once'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()
        TypeMirror output = Mock()
        def descriptors = [
                candidate(METHOD, 1, false, returnType, method),
                candidate(METHOD, 1, false, returnType, method),
        ]

        when:
        def result = filter.filter(descriptors).producing(output).toList()

        then:
        1 * types.isAssignable(returnType, output) >> true
        0 * _

        expect:
        result*.method == [method]
    }

    def 'isCallable requires a single-parameter, non-Object METHOD'() {
        ExecutableElement method = Mock()
        TypeMirror returnType = Mock()

        expect:
        filter.isCallable(candidate(kind, params, onObject, returnType, method)) == expected

        where:
        kind        | params | onObject | expected
        METHOD      | 1      | false    | true
        METHOD      | 0      | false    | false
        METHOD      | 2      | false    | false
        METHOD      | 1      | true     | false
        FIELD       | 1      | false    | false
        CONSTRUCTOR | 1      | false    | false
    }

    private CandidateDescriptor candidate(
            final ElementKind kind, final int parameterCount, final boolean enclosingIsObject,
            final TypeMirror returnType, final ExecutableElement method) {
        new CandidateDescriptor(kind, parameterCount, enclosingIsObject, returnType, method)
    }
}
