package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class PortSpec extends Specification {

    TypeMirror type = Mock()

    def 'the sourcing mode set is exactly the four closed modes, in declaration order'() {
        expect:
        Port.Sourcing.values().toList() == [
                Port.Sourcing.SUBTARGET, Port.Sourcing.REUSE, Port.Sourcing.REUSE_OR_MINT, Port.Sourcing.AMBIENT]
    }

    def 'a plain concrete port carries its name/type/nullness, no template, defaults to REUSE_OR_MINT, and has no key'() {
        when:
        def port = new Port('value', type, Nullability.NON_NULL)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template == null
        port.sourcing == Port.Sourcing.REUSE_OR_MINT
        port.key == ''
    }

    def 'a template port carries the given PortType template, defaults to REUSE_OR_MINT, and has no key'() {
        def template = PortType.variable(0)

        when:
        def port = new Port('value', type, Nullability.NON_NULL, template)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template.is(template)
        port.sourcing == Port.Sourcing.REUSE_OR_MINT
        port.key == ''
    }

    def 'Port.reuse builds a REUSE port carrying its name/type/nullness, with no template and no key'() {
        when:
        def port = Port.reuse('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        port.sourcing == Port.Sourcing.REUSE
        port.key == ''
    }

    def 'Port.subTarget builds a SUBTARGET port carrying its name/type/nullness, with no template and no key'() {
        when:
        def port = Port.subTarget('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        port.sourcing == Port.Sourcing.SUBTARGET
        port.key == ''
    }

    def 'Port.ambient builds an AMBIENT port carrying its name/type/nullness/key, with no template'() {
        when:
        def port = Port.ambient('order', type, Nullability.NON_NULL, 'order')

        then:
        port.name == 'order'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template == null
        port.sourcing == Port.Sourcing.AMBIENT
        port.key == 'order'
    }

    def 'Port.ambient rejects an empty key'() {
        when:
        Port.ambient('order', type, Nullability.NON_NULL, '')

        then:
        def error = thrown(IllegalArgumentException)
        0 * _

        expect:
        error.message == 'an AMBIENT port requires a non-empty key'
    }
}
