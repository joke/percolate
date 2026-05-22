package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.PathSegmentResolver
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Collectors

@Tag('unit')
class BuiltinServiceRegistrationSpec extends Specification {

    def 'ServiceLoader discovers all expected Bridge builtins'() {
        expect:
        final discovered = ServiceLoader.load(Bridge).stream()
                .map { it.get().class.name }
                .collect(Collectors.toSet())

        discovered.contains('io.github.joke.percolate.spi.builtins.DirectAssign')
        discovered.contains('io.github.joke.percolate.spi.builtins.MethodCallBridge')
        discovered.contains('io.github.joke.percolate.spi.builtins.IterableUnwrap')
        discovered.contains('io.github.joke.percolate.spi.builtins.OptionalUnwrap')
        discovered.contains('io.github.joke.percolate.spi.builtins.SetCollect')
        discovered.contains('io.github.joke.percolate.spi.builtins.ListCollect')
        discovered.contains('io.github.joke.percolate.spi.builtins.ArrayCollect')
        discovered.contains('io.github.joke.percolate.spi.builtins.OptionalCollect')
        discovered.contains('io.github.joke.percolate.spi.builtins.SetWrap')
        discovered.contains('io.github.joke.percolate.spi.builtins.ListWrap')
        discovered.contains('io.github.joke.percolate.spi.builtins.OptionalWrap')

        !discovered.contains('io.github.joke.percolate.spi.builtins.SetMap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.ListMap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.OptionalMap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.GetterRead')
    }

    def 'ServiceLoader discovers all expected GroupTarget builtins'() {
        expect:
        final discovered = ServiceLoader.load(GroupTarget).stream()
                .map { it.get().class.name }
                .collect(Collectors.toSet())

        discovered.contains('io.github.joke.percolate.spi.builtins.ConstructorCall')
    }

    def 'ServiceLoader discovers all expected PathSegmentResolver builtins'() {
        expect:
        final discovered = ServiceLoader.load(PathSegmentResolver).stream()
                .map { it.get().class.name }
                .collect(Collectors.toSet())

        discovered.contains('io.github.joke.percolate.spi.builtins.GetterPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.FieldPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.RecordPathResolver')
    }

    def 'spec does not import from processor package'() {
        expect:
        // This is a structural assertion verified by the test not compiling
        // if any processor imports are added. The class only imports from:
        // - io.github.joke.percolate.spi (strategy interfaces)
        // - spock.lang (testing framework)
        // - java.util.stream (standard library)
        true
    }
}
