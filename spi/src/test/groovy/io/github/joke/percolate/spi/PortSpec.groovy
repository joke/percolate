package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class PortSpec extends Specification {

    TypeMirror type = Mock()

    def 'the sourcing mode set is exactly the three closed modes, in declaration order'() {
        expect:
        Port.Sourcing.values().toList() == [Port.Sourcing.SUBTARGET, Port.Sourcing.REUSE, Port.Sourcing.REUSE_OR_MINT]
    }

    def 'a plain concrete port carries its name/type/nullness, no template, and defaults to REUSE_OR_MINT'() {
        when:
        def port = new Port('value', type, Nullability.NON_NULL)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template == null
        port.sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'a template port carries the given PortType template and defaults to REUSE_OR_MINT'() {
        def template = PortType.variable(0)

        when:
        def port = new Port('value', type, Nullability.NON_NULL, template)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template.is(template)
        port.sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'Port.reuse builds a REUSE port carrying its name/type/nullness, with no template'() {
        when:
        def port = Port.reuse('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        port.sourcing == Port.Sourcing.REUSE
    }

    def 'Port.subTarget builds a SUBTARGET port carrying its name/type/nullness, with no template'() {
        when:
        def port = Port.subTarget('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        port.sourcing == Port.Sourcing.SUBTARGET
    }
}
