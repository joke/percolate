package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import io.github.joke.percolate.spi.types.TypeRef
import io.github.joke.percolate.spi.types.TypeRefs
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class PortSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def 'the sourcing mode set is exactly the three closed modes, in declaration order'() {
        expect:
        Port.Sourcing.values().toList() == [Port.Sourcing.SUBTARGET, Port.Sourcing.REUSE, Port.Sourcing.REUSE_OR_MINT]
    }

    def 'a plain concrete port defaults to REUSE_OR_MINT'() {
        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'a template port defaults to REUSE_OR_MINT'() {
        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL, (PortType) null).sourcing == Port.Sourcing.REUSE_OR_MINT
    }

    def 'Port.reuse builds a REUSE port'() {
        expect:
        Port.reuse('value', javac.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.REUSE
    }

    def 'Port.subTarget builds a SUBTARGET port'() {
        expect:
        Port.subTarget('value', javac.STRING, Nullability.NON_NULL).sourcing == Port.Sourcing.SUBTARGET
    }

    def 'a concrete port derives typeRef from type'() {
        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL).typeRef == TypeRefs.of(javac.STRING)
    }

    def 'a null-template port derives typeRef from type, same as a concrete port'() {
        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL, (PortType) null).typeRef == TypeRefs.of(javac.STRING)
    }

    def 'a concrete template port derives typeRef from the template, not the representative type'() {
        def template = PortType.concrete(javac.INTEGER)

        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL, template).typeRef == TypeRefs.of(javac.INTEGER)
    }

    def 'a variable template port derives typeRef as a TypeRef.Variable, not the representative type'() {
        def template = PortType.variable(0)

        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL, template).typeRef == TypeRef.variable('V0')
    }

    def 'an app template port derives typeRef as a Declared with the argument shapes preserved'() {
        def setElement = javac.elements().getTypeElement('java.util.Set')
        def template = PortType.app(setElement, [PortType.variable(0)])

        expect:
        new Port('value', javac.STRING, Nullability.NON_NULL, template).typeRef ==
                TypeRef.declared('java.util.Set', TypeRef.variable('V0'))
    }

    def 'Port.reuse and Port.subTarget also derive typeRef from type'() {
        expect:
        Port.reuse('value', javac.STRING, Nullability.NON_NULL).typeRef == TypeRefs.of(javac.STRING)
        Port.subTarget('value', javac.STRING, Nullability.NON_NULL).typeRef == TypeRefs.of(javac.STRING)
    }
}
