package io.github.joke.percolate.architecture

import com.netflix.nebula.archrules.core.Runner
import io.github.joke.percolate.outside.OutsiderUsesCompilerServices
import io.github.joke.percolate.outside.UsesUpstreamJavaPoet
import io.github.joke.percolate.processor.internal.stages.MisnamedStep
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class TypeBoundaryRulesSpec extends Specification {

    /**
     * Also covers the classpath-resolution finding for {@code implement(...)}: the Stage supertype is
     * matched through the classpath rather than by being imported alongside the fixture.
     */
    def 'a Stage implementation without the Stage suffix is reported'() {
        expect:
        Runner.check(TypeBoundaryRules.STAGE_IMPLEMENTATIONS_ARE_NAMED_STAGE, MisnamedStep).hasViolation()
    }

    def 'a class outside the type boundary using compiler services is reported'() {
        expect:
        Runner.check(TypeBoundaryRules.COMPILER_SERVICES_ARE_CONFINED, OutsiderUsesCompilerServices).hasViolation()
    }

    def 'a class importing unrelocated upstream JavaPoet is reported'() {
        expect:
        Runner.check(TypeBoundaryRules.NO_UPSTREAM_JAVAPOET, UsesUpstreamJavaPoet).hasViolation()
    }
}
