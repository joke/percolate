package io.github.joke.percolate.architecture

import com.netflix.nebula.archrules.core.Runner
import io.github.joke.percolate.outside.OutsiderTouchesEngineInternals
import io.github.joke.percolate.processor.violators.EngineReadsRawAnnotation
import io.github.joke.percolate.processor.violators.ProcessorReadsMappingAnnotation
import io.github.joke.percolate.spi.builtins.violators.StrategyTouchesGraph
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class EngineEncapsulationRulesSpec extends Specification {

    def 'a processor class depending on a mapping annotation is reported'() {
        expect:
        Runner.check(EngineEncapsulationRules.PROCESSOR_READS_NO_MAPPING_ANNOTATION, ProcessorReadsMappingAnnotation)
                .hasViolation()
    }

    /**
     * Also covers design's classpath-resolution finding: the rule tests
     * {@code isAssignableTo("javax.lang.model.element.Element")} on the call target's owner, which only
     * resolves because ArchUnit reads the non-imported javax hierarchy off the classpath. If that resolution
     * were unavailable this fixture would stop being reported.
     */
    def 'an engine class reading a raw annotation off an Element is reported'() {
        expect:
        Runner.check(EngineEncapsulationRules.ENGINE_READS_NO_RAW_ANNOTATION, EngineReadsRawAnnotation).hasViolation()
    }

    def 'a strategy implementation touching the engine graph is reported'() {
        expect:
        Runner.check(EngineEncapsulationRules.STRATEGY_MAY_NOT_TOUCH_THE_GRAPH, StrategyTouchesGraph).hasViolation()
    }

    def 'a class outside the engine reaching a processor internal is reported'() {
        expect:
        Runner.check(EngineEncapsulationRules.ENGINE_INTERNALS_ARE_ENCAPSULATED, OutsiderTouchesEngineInternals)
                .hasViolation()
    }
}
