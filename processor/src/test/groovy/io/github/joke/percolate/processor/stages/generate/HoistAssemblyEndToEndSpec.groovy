package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Focused coverage for the hoist-assembly-args-to-locals change: arguments of a multi-argument (n-ary) assembly
 * call materialise as named locals while single-port chains and the return-root stay inline, and a Value shared by
 * more than one port is emitted (and evaluated) once.
 */
@Tag('integration')
class HoistAssemblyEndToEndSpec extends Specification {

    def 'constructor arguments hoist to locals, single-port chains stay inline, and the return-root stays inline'() {
        given:
        def person = JavaFileObjects.forSourceLines('test.Person',
                'package test;',
                'public final class Person {',
                '    private final Address address; private final String first;',
                '    public Person(Address address, String first) { this.address = address; this.first = first; }',
                '    public Address getAddress() { return address; }',
                '    public String getFirst() { return first; }',
                '}')
        def address = JavaFileObjects.forSourceLines('test.Address',
                'package test;',
                'public final class Address {',
                '    private final String street;',
                '    public Address(String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')
        def human = JavaFileObjects.forSourceLines('test.Human',
                'package test;',
                'public final class Human {',
                '    private final String street; private final String first;',
                '    public Human(String street, String first) { this.street = street; this.first = first; }',
                '    public String getStreet() { return street; }',
                '    public String getFirst() { return first; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "street", source = "person.address.street")',
                '    @Map(target = "first", source = "person.first")',
                '    Human map(Person person);',
                '}')

        when:
        Compilation compilation = compile(person, address, human, mapper)

        then:
        compilation.errors().empty
        def content = generated(compilation, 'test.MImpl')

        and: 'each n-ary constructor argument hoists to a local named after its target slot'
        content.contains('String street = person.getAddress().getStreet();')
        content.contains('String first = person.getFirst();')

        and: 'the multi-segment source path stays one inline chain, not split into an intermediate local'
        !content.contains('= person.getAddress();')

        and: 'the bare parameter leaf is rendered inline, never aliased into its own local'
        !content.contains('= person;')

        and: 'the return-root renders inline (no trailing temporary)'
        content.contains('return new Human(street, first);')
    }

    def 'a Value shared by two consumers is materialised once and referenced at each use'() {
        given:
        def person = JavaFileObjects.forSourceLines('test.SPerson',
                'package test;',
                'public final class SPerson {',
                '    private final String name;',
                '    public SPerson(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def human = JavaFileObjects.forSourceLines('test.SHuman',
                'package test;',
                'public final class SHuman {',
                '    private final String first; private final String display;',
                '    public SHuman(String first, String display) { this.first = first; this.display = display; }',
                '    public String getFirst() { return first; }',
                '    public String getDisplay() { return display; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.SM',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface SM {',
                '    @Map(target = "first", source = "person.name")',
                '    @Map(target = "display", source = "person.name")',
                '    SHuman map(SPerson person);',
                '}')

        when:
        Compilation compilation = compile(person, human, mapper)

        then:
        compilation.errors().empty
        def content = generated(compilation, 'test.SMImpl')

        and: 'the shared source expression is evaluated exactly once (one hoisted local named after its source slot)'
        content.count('person.getName()') == 1
        content.contains('String name = person.getName();')

        and: 'that single local is referenced at each of the two use sites'
        content.count('= name;') == 2
        content.contains('return new SHuman(')
    }

    def 'the local declaration style is configurable via percolate.locals.final / percolate.locals.var'() {
        given:
        def person = JavaFileObjects.forSourceLines('test.CPerson',
                'package test;',
                'public final class CPerson {',
                '    private final String first; private final String last;',
                '    public CPerson(String first, String last) { this.first = first; this.last = last; }',
                '    public String getFirst() { return first; }',
                '    public String getLast() { return last; }',
                '}')
        def human = JavaFileObjects.forSourceLines('test.CHuman',
                'package test;',
                'public final class CHuman {',
                '    private final String first; private final String last;',
                '    public CHuman(String first, String last) { this.first = first; this.last = last; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.CM',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface CM {',
                '    @Map(target = "first", source = "person.first")',
                '    @Map(target = "last", source = "person.last")',
                '    CHuman map(CPerson person);',
                '}')

        when:
        Compilation compilation = compileWith(options, person, human, mapper)

        then: 'the generated mapper compiles and the hoisted local uses the configured style'
        compilation.errors().empty
        generated(compilation, 'test.CMImpl').contains(declaration)

        where: 'each flag combination renders its declaration prefix; the slot name is unaffected'
        options                                                          | declaration
        []                                                               | 'String first = person.getFirst();'
        ['-Apercolate.locals.final=true']                                | 'final String first = person.getFirst();'
        ['-Apercolate.locals.var=true']                                  | 'var first = person.getFirst();'
        ['-Apercolate.locals.final=true', '-Apercolate.locals.var=true'] | 'final var first = person.getFirst();'
    }

    private static Compilation compile(JavaFileObject... sources) {
        compileWith([], sources)
    }

    private static Compilation compileWith(List<String> options, JavaFileObject... sources) {
        Compiler.javac().withProcessors(new PercolateProcessor()).withOptions(options).compile(sources)
    }

    private static String generated(final Compilation compilation, final String fqn) {
        def file = compilation.generatedSourceFile(fqn)
        assert file.present
        file.get().getCharContent(true).toString()
    }
}
