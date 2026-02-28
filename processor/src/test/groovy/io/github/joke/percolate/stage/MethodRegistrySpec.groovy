package io.github.joke.percolate.stage

import spock.lang.Specification

class MethodRegistrySpec extends Specification {

    def "lookup returns empty for unknown type pair"() {
        given:
        def registry = new MethodRegistry()

        when:
        def result = registry.lookup("test.A", "test.B")

        then:
        !result.isPresent()
    }

    def "register and lookup by type pair key"() {
        given:
        def registry = new MethodRegistry()
        def entry = new RegistryEntry(null, null)  // stub

        when:
        registry.register("test.A", "test.B", entry)

        then:
        registry.lookup("test.A", "test.B").isPresent()
        registry.lookup("test.A", "test.B").get() == entry
    }

    def "lookup is exact — different pair returns empty"() {
        given:
        def registry = new MethodRegistry()
        registry.register("test.A", "test.B", new RegistryEntry(null, null))

        when:
        def result = registry.lookup("test.A", "test.C")

        then:
        !result.isPresent()
    }
}
