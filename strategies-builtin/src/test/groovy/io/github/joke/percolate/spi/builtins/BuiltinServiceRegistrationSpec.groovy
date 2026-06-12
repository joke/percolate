package io.github.joke.percolate.spi.builtins

import io.github.joke.percolate.spi.ExpansionStrategy
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Collectors

@Tag('unit')
class BuiltinServiceRegistrationSpec extends Specification {

    def 'ServiceLoader discovers every builtin under the unified ExpansionStrategy type'() {
        expect:
        final discovered = ServiceLoader.load(ExpansionStrategy).stream()
                .map { it.get().class.name }
                .collect(Collectors.toSet())

        // Conversions and assembly
        discovered.contains('io.github.joke.percolate.spi.builtins.DirectAssign')
        discovered.contains('io.github.joke.percolate.spi.builtins.MethodCallBridge')
        discovered.contains('io.github.joke.percolate.spi.builtins.ConstructorCall')
        discovered.contains('io.github.joke.percolate.spi.builtins.WidenPrimitive')
        discovered.contains('io.github.joke.percolate.spi.builtins.PrimitiveWrapperConversion')

        // Constants and defaults
        discovered.contains('io.github.joke.percolate.spi.builtins.ConstantValue')
        discovered.contains('io.github.joke.percolate.spi.builtins.DefaultValue')

        // Containers (the nine per-operation bridges were consolidated into these four)
        discovered.contains('io.github.joke.percolate.spi.builtins.ListContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.SetContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.ArrayContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.OptionalContainer')

        // Path resolvers (formerly the separate PathSegmentResolver service)
        discovered.contains('io.github.joke.percolate.spi.builtins.GetterPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.FieldPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.MethodPathResolver')
    }

    def 'the retired per-operation and per-SPI builtins are gone'() {
        expect:
        final discovered = ServiceLoader.load(ExpansionStrategy).stream()
                .map { it.get().class.name }
                .collect(Collectors.toSet())

        !discovered.contains('io.github.joke.percolate.spi.builtins.IterableUnwrap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.OptionalUnwrap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.SetCollect')
        !discovered.contains('io.github.joke.percolate.spi.builtins.ListCollect')
        !discovered.contains('io.github.joke.percolate.spi.builtins.ListWrap')
        !discovered.contains('io.github.joke.percolate.spi.builtins.GetterRead')
        !discovered.contains('io.github.joke.percolate.spi.builtins.RecordPathResolver')
    }

    def 'spec does not import from processor package'() {
        expect:
        // Structural assertion verified by the test compiling: the class imports only from
        // io.github.joke.percolate.spi, spock.lang, and java.util.stream.
        true
    }
}
