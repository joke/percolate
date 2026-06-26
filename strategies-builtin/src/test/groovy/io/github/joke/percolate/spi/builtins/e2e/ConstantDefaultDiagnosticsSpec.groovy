package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic
import javax.tools.JavaFileObject

@Tag('integration')
class ConstantDefaultDiagnosticsSpec extends Specification {

    def 'an uncoercible constant produces a targeted coercion-failure error'() {
        given:
        def thing = JavaFileObjects.forSourceLines('test.Thing',
                'package test;',
                'public final class Thing {',
                '    private final int count;',
                '    public Thing(int count) { this.count = count; }',
                '    public int getCount() { return count; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "count", constant = "abc")',
                '    Thing map(Object in);',
                '}')

        when:
        Compilation compilation = compile(thing, mapper)

        then:
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        errors.any { it.getMessage(null).contains("cannot coerce 'abc' to int") }
    }

    def 'a default on a NON_NULL reference source is rejected as a dead default'() {
        given:
        def packageInfo = JavaFileObjects.forSourceLines('test.package-info',
                '@org.jspecify.annotations.NullMarked',
                'package test;')
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
                '    @Map(target = "name", source = "in.name", defaultValue = "x")',
                '    Human map(Person in);',
                '}')

        when:
        Compilation compilation = compile(packageInfo, human, person, mapper)

        then:
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        errors.any { it.getMessage(null).contains('can never fire') }
    }

    def 'a default on a primitive source is rejected as a dead default'() {
        given:
        def human = JavaFileObjects.forSourceLines('test.Human',
                'package test;',
                'public final class Human {',
                '    private final int age;',
                '    public Human(int age) { this.age = age; }',
                '    public int getAge() { return age; }',
                '}')
        def person = JavaFileObjects.forSourceLines('test.Person',
                'package test;',
                'public final class Person {',
                '    private final int age;',
                '    public Person(int age) { this.age = age; }',
                '    public int getAge() { return age; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "age", source = "in.age", defaultValue = "0")',
                '    Human map(Person in);',
                '}')

        when:
        Compilation compilation = compile(human, person, mapper)

        then:
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        errors.any { it.getMessage(null).contains('can never fire') }
    }

    private static Compilation compile(JavaFileObject... sources) {
        PercolateCompiler.compile(sources)
    }
}
