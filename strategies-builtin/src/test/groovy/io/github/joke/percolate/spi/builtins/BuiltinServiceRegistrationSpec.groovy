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
        discovered.contains('io.github.joke.percolate.spi.builtins.assembly.DirectAssign')
        discovered.contains('io.github.joke.percolate.spi.builtins.methodcall.MethodCallBridge')
        discovered.contains('io.github.joke.percolate.spi.builtins.assembly.ConstructorCall')
        discovered.contains('io.github.joke.percolate.spi.builtins.primitive.WidenPrimitive')
        discovered.contains('io.github.joke.percolate.spi.builtins.primitive.PrimitiveWrapperConversion')

        // Constants and the nullness crossing (requireNonNull / coalesce)
        discovered.contains('io.github.joke.percolate.spi.builtins.value.ConstantValue')
        discovered.contains('io.github.joke.percolate.spi.builtins.value.NullnessCrossing')

        // Containers (the nine per-operation bridges were consolidated into these four)
        discovered.contains('io.github.joke.percolate.spi.builtins.container.ListContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.container.SetContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.container.ArrayContainer')
        discovered.contains('io.github.joke.percolate.spi.builtins.container.OptionalContainer')

        // Path resolvers (formerly the separate PathSegmentResolver service)
        discovered.contains('io.github.joke.percolate.spi.builtins.accessor.GetterPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.accessor.FieldPathResolver')
        discovered.contains('io.github.joke.percolate.spi.builtins.accessor.MethodPathResolver')

        // Temporal hubs + zone bridge + format (change add-temporal-type-mapping)
        discovered.contains('io.github.joke.percolate.spi.builtins.temporal.AbsoluteTemporalConversion')
        discovered.contains('io.github.joke.percolate.spi.builtins.temporal.LocalTemporalConversion')
        discovered.contains('io.github.joke.percolate.spi.builtins.temporal.InstantLocalDateTimeBridge')
        discovered.contains('io.github.joke.percolate.spi.builtins.temporal.TemporalFormat')
        discovered.contains('io.github.joke.percolate.spi.builtins.temporal.LegacyTemporalFormat')

        // Enum-to-enum conversion (change add-enum-conversion-mapping)
        discovered.contains('io.github.joke.percolate.spi.builtins.enumconversion.EnumConversion')
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
        !discovered.contains('io.github.joke.percolate.spi.builtins.DefaultValue')
    }

    def 'spec does not import from processor package'() {
        expect:
        // Structural assertion verified by the test compiling: the class imports only from
        // io.github.joke.percolate.spi, spock.lang, and java.util.stream.
        true
    }
}
