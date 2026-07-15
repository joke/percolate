package io.github.joke.percolate.docs.abstractclass

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's mapper-structure "Abstract classes" section. {@code VehicleMapper} is real source
 * compiled by the ordinary {@code compileTestJava} task through the real starter — no compile-testing.
 * It restates, as a manual-owned fixture, the abstract-class shape {@code AssembleMapperTypeFeatureSpec}
 * (processor module) already proves at the compile level: the generated {@code VehicleMapperImpl} extends
 * the abstract mapper class rather than implementing an interface.
 */
@Tag('integration')
class AbstractClassMapperDocExampleSpec extends Specification {

    def 'the generated mapper extends the abstract class and maps the license plate'() {
        def mapper = new VehicleMapperImpl()

        expect:
        mapper instanceof VehicleMapper
        mapper.map(new Car('XYZ-123')).plate == 'XYZ-123'
    }
}
