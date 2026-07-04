package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.test.fixtures.CallableFixtures
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.spi.ThisReceiver
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeKind

/**
 * {@link DiscoverCallableMethodsStage} seam, unit-tested directly: {@code run} indexes the mapper type's
 * single-parameter, non-{@code Object} methods by return type; {@code CallableMethods.producing} then answers with the
 * candidates whose return type is assignable to the demanded output, each carrying the {@link ThisReceiver}.
 */
@Tag('unit')
class DiscoverCallableMethodsStageSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    DiscoverCallableMethodsStage stage = new DiscoverCallableMethodsStage(javac.elements(), javac.types())

    def setupSpec() {
        // prime the fixture + its methods' parameter/return closures single-threaded (see ExpandStageDriverSpec)
        javac.of(Person)
        javac.of(Human)
        javac.of(CallableFixtures)
    }

    def 'run indexes only single-parameter methods; producing returns Human-assignable candidates with a this-receiver'() {
        given:
        def ctx = new MapperContext(javac.of(CallableFixtures))

        when:
        stage.run(ctx)
        def candidates = ctx.callableMethods.producing(javac.of(Human).asType()).toList()

        then: 'only makeHuman(Person) qualifies — noArg (zero-param) and pair (two-param) are excluded'
        candidates.collect { it.method.simpleName.toString() } == ['makeHuman']

        and: 'the candidate is invoked on the mapper itself'
        candidates[0].receiver == ThisReceiver.INSTANCE
    }

    def 'producing filters by assignable return type'() {
        given:
        def ctx = new MapperContext(javac.of(CallableFixtures))

        when:
        stage.run(ctx)

        then: 'a String demand returns describe(Person), not the Human producers'
        ctx.callableMethods.producing(javac.of(String).asType()).toList()
                .collect { it.method.simpleName.toString() } == ['describe']
    }

    def 'Object methods are excluded even though equals(Object) is single-parameter'() {
        given:
        def ctx = new MapperContext(javac.of(CallableFixtures))

        when:
        stage.run(ctx)

        then: 'no fixture method returns boolean, so a non-empty result here would mean equals(Object) leaked in'
        ctx.callableMethods.producing(javac.types().getPrimitiveType(TypeKind.BOOLEAN)).toList().empty
    }
}
