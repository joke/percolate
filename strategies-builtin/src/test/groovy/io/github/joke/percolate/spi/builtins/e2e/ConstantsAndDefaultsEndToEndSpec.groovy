package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

@Tag('integration')
class ConstantsAndDefaultsEndToEndSpec extends Specification {

    def 'constant String and constant primitive are produced with no source'() {
        given:
        def thing = JavaFileObjects.forSourceLines('test.Thing',
                'package test;',
                'public final class Thing {',
                '    private final String status; private final int count;',
                '    public Thing(String status, int count) { this.status = status; this.count = count; }',
                '    public String getStatus() { return status; }',
                '    public int getCount() { return count; }',
                '}')
        def in_ = JavaFileObjects.forSourceLines('test.In', 'package test;', 'public final class In {}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "status", constant = "ACTIVE")',
                '    @Map(target = "count", constant = "42")',
                '    Thing map(In in);',
                '}')

        when:
        Compilation compilation = compile(thing, in_, mapper)

        then:
        compilation.errors().empty
        def content = generated(compilation, 'test.MImpl')
        and: 'each n-ary constructor argument hoists to a target-slot-named local, then the return-root calls inline'
        content.contains('String status = "ACTIVE";')
        content.contains('int count = 42;')
        content.contains('return new Thing(status, count)')
    }

    def 'a default on a nullable scalar source coalesces with requireNonNullElse'() {
        given:
        def human = JavaFileObjects.forSourceLines('test.Human',
                'package test;',
                'public final class Human {',
                '    private final String name;',
                '    public Human(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def person = JavaFileObjects.forSourceLines('test.Person',
                'package test;',
                'public final class Person {',
                '    private final String name;',
                '    public Person(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "name", source = "in.name", defaultValue = "unknown")',
                '    Human map(Person in);',
                '}')

        when:
        Compilation compilation = compile(human, person, mapper)

        then:
        compilation.errors().empty
        def content = generated(compilation, 'test.MImpl')
        content.contains('requireNonNullElse(in.getName(), "unknown")')
        !content.contains('requireNonNull(in.getName(),') // no plain null guard
    }

    def 'a default on an Optional source coalesces with orElse'() {
        given:
        def human = JavaFileObjects.forSourceLines('test.Human',
                'package test;',
                'public final class Human {',
                '    private final String name;',
                '    public Human(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def person = JavaFileObjects.forSourceLines('test.Person',
                'package test;',
                'import java.util.Optional;',
                'public final class Person {',
                '    private final String name;',
                '    public Person(String name) { this.name = name; }',
                '    public Optional<String> getName() { return Optional.ofNullable(name); }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "name", source = "in.name", defaultValue = "unknown")',
                '    Human map(Person in);',
                '}')

        when:
        Compilation compilation = compile(human, person, mapper)

        then:
        compilation.errors().empty
        def content = generated(compilation, 'test.MImpl')
        content.contains('in.getName().orElse("unknown")')
    }

    private static Compilation compile(JavaFileObject... sources) {
        PercolateCompiler.compile(sources)
    }

    private static String generated(final Compilation compilation, final String fqn) {
        def file = compilation.generatedSourceFile(fqn)
        assert file.present
        file.get().getCharContent(true).toString()
    }
}
