package io.github.joke.percolate.architecture

import com.netflix.nebula.archrules.core.Runner
import io.github.joke.percolate.processor.internal.stages.expand.violators.OversizedClass
import io.github.joke.percolate.spi.PublishedHook
import io.github.joke.percolate.spi.builtins.violators.BuiltinsUnusedProtected
import io.github.joke.percolate.violators.HasPrivateMethod
import io.github.joke.percolate.violators.HasStaticMethod
import io.github.joke.percolate.violators.HasUnusedProtectedMethod
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MethodShapeRulesSpec extends Specification {

    def 'a private method is reported'() {
        expect:
        Runner.check(MethodShapeRules.NO_PRIVATE_METHODS, HasPrivateMethod).hasViolation()
    }

    def 'a static method outside a genuine static context is reported'() {
        expect:
        Runner.check(MethodShapeRules.NO_STATIC_OUTSIDE_GENUINE_CONTEXT, HasStaticMethod).hasViolation()
    }

    def 'an unused unannotated protected method is reported'() {
        expect:
        Runner.check(MethodShapeRules.UNUSED_PROTECTED_METHODS_ARE_MARKED, HasUnusedProtectedMethod).hasViolation()
    }

    def 'a class over the method-count ceiling is reported'() {
        expect:
        Runner.check(MethodShapeRules.DECOMPOSED_CLASSES_STAY_WITHIN_CEILING, OversizedClass).hasViolation()
    }

    /**
     * Design D5, exemption direction one. A concrete protected method on the published spi surface is the
     * extension contract offered to third-party strategy authors, and per-source-set evaluation cannot see
     * its downstream overriders anyway - so it passes unannotated.
     */
    def 'a published spi template-method hook is exempt'() {
        expect:
        !Runner.check(MethodShapeRules.UNUSED_PROTECTED_METHODS_ARE_MARKED, PublishedHook).hasViolation()
    }

    /**
     * Design D5, exemption direction two. spi.builtins shares the spi package root but is an internal
     * module, so the exemption must not reach it.
     */
    def 'an unused protected method in spi builtins is still reported'() {
        expect:
        Runner.check(MethodShapeRules.UNUSED_PROTECTED_METHODS_ARE_MARKED, BuiltinsUnusedProtected).hasViolation()
    }
}
