package io.github.joke.percolate.docs.enummapping

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's enum-mapping page. {@code EnumMappingMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Exercises mirrored same-name matching,
 * {@code @MapEnum} overrides (with an unreachable target-only constant), and a bean member bridging through a
 * declared conversion method.
 */
@Tag('integration')
class EnumMappingDocExampleSpec extends Specification {

    def mapper = new EnumMappingMapperImpl()

    def 'mirrored enums map by name with no @MapEnum directive'() {
        expect:
        mapper.toStatus(StatusDto.CREATED) == Status.CREATED
        mapper.toStatus(StatusDto.FULFILLED) == Status.FULFILLED
        mapper.toStatus(StatusDto.ARCHIVED) == Status.ARCHIVED
    }

    def '@MapEnum overrides same-name matching for differently-named constants'() {
        expect:
        mapper.toOrderStatus(MyStatus.NEW) == OrderStatus.CREATED
        mapper.toOrderStatus(MyStatus.COMPLETED) == OrderStatus.FULFILLED
    }

    def 'a bean member of the target enum bridges through the declared conversion method'() {
        def dto = new OrderDto(MyStatus.COMPLETED)

        expect:
        mapper.map(dto).status == OrderStatus.FULFILLED
    }
}
