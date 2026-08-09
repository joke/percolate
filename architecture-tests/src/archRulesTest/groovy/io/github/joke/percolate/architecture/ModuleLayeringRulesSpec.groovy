package io.github.joke.percolate.architecture

import com.netflix.nebula.archrules.core.Runner
import io.github.joke.percolate.AnnotationTouchesSpi
import io.github.joke.percolate.processor.violators.EngineTouchesStrategy
import io.github.joke.percolate.spi.SpiTouchesEngine
import io.github.joke.percolate.spi.builtins.violators.StrategySide
import io.github.joke.percolate.test.violators.HarnessTouchesStrategy
import spock.lang.Specification
import spock.lang.Tag

/**
 * Negative coverage for the layering rules. Every rule sets {@code allowEmptyShould(true)} - it has to,
 * because per-source-set evaluation means a rule scoped to one module matches nothing in the others - so a
 * mistyped package coordinate would match nothing everywhere and pass silently. These fixtures are the only
 * thing that distinguishes "the rule found no violation" from "the rule found no classes".
 */
@Tag('unit')
class ModuleLayeringRulesSpec extends Specification {

    def 'the engine reaching a strategy module is reported'() {
        expect:
        Runner.check(ModuleLayeringRules.ENGINE_HAS_NO_EDGE_TO_STRATEGY, EngineTouchesStrategy).hasViolation()
    }

    def 'the harness reaching a strategy module is reported'() {
        expect:
        Runner.check(ModuleLayeringRules.HARNESS_IS_STRATEGY_AGNOSTIC, HarnessTouchesStrategy).hasViolation()
    }

    def 'spi reaching the engine is reported'() {
        expect:
        Runner.check(ModuleLayeringRules.SPI_DEPENDS_ON_NEITHER_SIDE, SpiTouchesEngine).hasViolation()
    }

    def 'the annotations reaching spi is reported'() {
        expect:
        Runner.check(ModuleLayeringRules.ANNOTATIONS_DEPEND_ON_NOTHING, AnnotationTouchesSpi).hasViolation()
    }

    def 'a class with no forbidden edge is not reported'() {
        expect:
        !Runner.check(ModuleLayeringRules.ENGINE_HAS_NO_EDGE_TO_STRATEGY, StrategySide).hasViolation()
    }
}
