package io.github.joke.percolate.processor.graph

import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class LocationRoleSpec extends Specification {

    def 'TargetLocation is FREE (return root and named target alike)'() {
        expect:
        new TargetLocation(TargetPath.of('')).role() == Location.Role.FREE
        new TargetLocation(TargetPath.of('name')).role() == Location.Role.FREE
    }

    def 'a single-segment SourceLocation is a LEAF (a parameter root)'() {
        expect:
        new SourceLocation(AccessPath.of('p')).role() == Location.Role.LEAF
    }

    def 'a multi-segment SourceLocation is ACCESS (an accessor chain)'() {
        expect:
        new SourceLocation(new AccessPath(['p', 'address', 'street'])).role() == Location.Role.ACCESS
    }

    def 'an ElementLocation is a LEAF (a container element root)'() {
        expect:
        new ElementLocation().role() == Location.Role.LEAF
    }

    def 'a ConstantLocation is CONSTANT'() {
        expect:
        new ConstantLocation('42').role() == Location.Role.CONSTANT
    }
}
