package io.github.joke.percolate.stage

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification

import static com.google.testing.compile.CompilationSubject.assertThat

class ValidateStageConversionSpec extends Specification {

    def "fails when mapper method is missing for type conversion"() {
        given: 'a mapper referencing Venue→FlatVenue without a mapVenue method'
        def mapper = JavaFileObjects.forSourceLines('test.TicketMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue { public String getName() { return ""; } }',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order { public Venue getVenue() { return null; } }',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).failed()
    }

    def "succeeds when mapper method exists for type conversion"() {
        given:
        def mapper = JavaFileObjects.forSourceLines('test.TicketMapper',
            'package test;',
            'import io.github.joke.percolate.Mapper;',
            'import io.github.joke.percolate.Map;',
            '@Mapper',
            'public interface TicketMapper {',
            '    @Map(target = "venue", source = "venue")',
            '    FlatOrder map(Order order);',
            '    FlatVenue mapVenue(Venue venue);',
            '}',
        )
        def venue = JavaFileObjects.forSourceLines('test.Venue',
            'package test;',
            'public class Venue { public String getName() { return ""; } }',
        )
        def flatVenue = JavaFileObjects.forSourceLines('test.FlatVenue',
            'package test;',
            'public class FlatVenue {',
            '    public final String name;',
            '    public FlatVenue(String name) { this.name = name; }',
            '}',
        )
        def order = JavaFileObjects.forSourceLines('test.Order',
            'package test;',
            'public class Order { public Venue getVenue() { return null; } }',
        )
        def flatOrder = JavaFileObjects.forSourceLines('test.FlatOrder',
            'package test;',
            'public class FlatOrder {',
            '    public final FlatVenue venue;',
            '    public FlatOrder(FlatVenue venue) { this.venue = venue; }',
            '}',
        )

        when:
        Compilation compilation = Compiler.javac()
            .withProcessors(new PercolateProcessor())
            .compile(mapper, venue, flatVenue, order, flatOrder)

        then:
        assertThat(compilation).succeeded()
    }
}
