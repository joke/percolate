package io.github.joke.percolate.processor.spi

import io.github.joke.percolate.processor.spi.builtins.GetterRead
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Collectors
import java.util.stream.StreamSupport

@Tag('unit')
class StrategyRegistrationSpec extends Specification {

    def 'ServiceLoader.load(SourceStep.class) includes GetterRead'() {
        given:
        def loader = ServiceLoader.load(SourceStep.class)
        def strategies = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toUnmodifiableList())

        when:
        def hasGetterRead = strategies.any { it instanceof GetterRead }

        then:
        hasGetterRead
    }

    def 'provided List<SourceStep> is sorted by FQN'() {
        given:
        def loader = ServiceLoader.load(SourceStep.class)
        def list = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toUnmodifiableList())
        def sorted = list.stream()
                .sorted({ a, b -> a.class.name.compareTo(b.class.name) })
                .collect(Collectors.toUnmodifiableList())

        when:
        def names = list*.class*.name
        def sortedNames = sorted*.class*.name

        then:
        names == sortedNames
    }

    def 'provided List<GroupTarget> is sorted by FQN'() {
        given:
        def loader = ServiceLoader.load(GroupTarget.class)
        def list = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toUnmodifiableList())
        def sorted = list.stream()
                .sorted({ a, b -> a.class.name.compareTo(b.class.name) })
                .collect(Collectors.toUnmodifiableList())

        when:
        def names = list*.class*.name
        def sortedNames = sorted*.class*.name

        then:
        names == sortedNames
    }

    def 'provided List<Bridge> is sorted by FQN'() {
        given:
        def loader = ServiceLoader.load(Bridge.class)
        def list = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toUnmodifiableList())
        def sorted = list.stream()
                .sorted({ a, b -> a.class.name.compareTo(b.class.name) })
                .collect(Collectors.toUnmodifiableList())

        when:
        def names = list*.class*.name
        def sortedNames = sorted*.class*.name

        then:
        names == sortedNames
    }

    def 'loaded strategies are wrapped in unmodifiable list'() {
        given:
        def loader = ServiceLoader.load(SourceStep.class)
        def list = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toUnmodifiableList())

        when:
        list.add(Mock(SourceStep))

        then:
        thrown(UnsupportedOperationException)
    }
}
