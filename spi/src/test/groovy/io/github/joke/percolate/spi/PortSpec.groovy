package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class PortSpec extends Specification {

    TypeMirror type = Mock()

    def 'the selector set is exactly the two closed selectors, in declaration order'() {
        expect:
        Port.Selector.values().toList() == [Port.Selector.BY_TYPE, Port.Selector.BY_NAME]
    }

    def 'the on-miss set is exactly the three closed rules, in declaration order'() {
        expect:
        Port.OnMiss.values().toList() == [Port.OnMiss.DECLINE, Port.OnMiss.MINT, Port.OnMiss.REQUIRE]
    }

    def 'a plain concrete port carries its name/type/nullness, no template, defaults to BY_TYPE/MINT, not a sub-target, no binding name'() {
        when:
        def port = new Port('value', type, Nullability.NON_NULL)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template == null
        !port.subTarget
        port.selector == Port.Selector.BY_TYPE
        port.onMiss == Port.OnMiss.MINT
        port.bindingName == ''
    }

    def 'a template port carries the given PortType template, defaults to BY_TYPE/MINT, no binding name'() {
        def template = PortType.variable(0)

        when:
        def port = new Port('value', type, Nullability.NON_NULL, template)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template.is(template)
        !port.subTarget
        port.selector == Port.Selector.BY_TYPE
        port.onMiss == Port.OnMiss.MINT
        port.bindingName == ''
    }

    def 'Port.byType builds the same BY_TYPE/MINT port as the plain constructor'() {
        when:
        def port = Port.byType('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        !port.subTarget
        port.selector == Port.Selector.BY_TYPE
        port.onMiss == Port.OnMiss.MINT
        port.bindingName == ''
    }

    def 'Port.byTypeOrDecline builds a BY_TYPE/DECLINE port carrying its name/type/nullness, with no template and no binding name'() {
        when:
        def port = Port.byTypeOrDecline('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        !port.subTarget
        port.selector == Port.Selector.BY_TYPE
        port.onMiss == Port.OnMiss.DECLINE
        port.bindingName == ''
    }

    def 'Port.subTarget builds a sub-target port carrying its name/type/nullness, with no template, no selector/on-miss and no binding name'() {
        when:
        def port = Port.subTarget('value', type, Nullability.NULLABLE)

        then:
        port.name == 'value'
        port.type.is(type)
        port.nullness == Nullability.NULLABLE
        port.template == null
        port.subTarget
        port.selector == null
        port.onMiss == null
        port.bindingName == ''
    }

    def 'Port.byName builds a BY_NAME/REQUIRE port carrying its name/type/nullness/binding name, with no template'() {
        when:
        def port = Port.byName('order', type, Nullability.NON_NULL, 'order')

        then:
        port.name == 'order'
        port.type.is(type)
        port.nullness == Nullability.NON_NULL
        port.template == null
        !port.subTarget
        port.selector == Port.Selector.BY_NAME
        port.onMiss == Port.OnMiss.REQUIRE
        port.bindingName == 'order'
    }

    def 'Port.byName rejects an empty binding name'() {
        when:
        Port.byName('order', type, Nullability.NON_NULL, '')

        then:
        def error = thrown(IllegalArgumentException)
        0 * _

        expect:
        error.message == 'a BY_NAME port requires a non-empty binding name'
    }
}
