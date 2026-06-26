package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic

@Tag('integration')
class EndToEndCodegenSpec extends Specification {

    def 'generated class compiles and has the expected shape'() {
        given:
        def person = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Person',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class Person {',
                '    private final String firstName;',
                '    public Person(final String firstName) { this.firstName = firstName; }',
                '    public String getFirstName() { return firstName; }',
                '}')

        def human = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Human',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class Human {',
                '    private final String firstName;',
                '    public Human(final String firstName) { this.firstName = firstName; }',
                '    public String getFirstName() { return firstName; }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.PersonMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface PersonMapper {',
                '    @Map(target = "firstName", source = "person.firstName")',
                '    Human map(Person person);',
                '}')

        when:
        Compilation compilation = PercolateCompiler.compile(person, human, mapper)

        then: 'compilation succeeded'
        compilation.errors().empty

        and: 'generated source file exists'
        def generatedSource = compilation
                .generatedSourceFile('io.github.joke.percolate.processor.test.fixtures.PersonMapperImpl')
        generatedSource.present

        and: 'class has correct package, name, visibility, supertype'
        def content = generatedSource.get().getCharContent(true).toString()
        content.contains('package io.github.joke.percolate.processor.test.fixtures;')
        content.contains('public final class PersonMapperImpl implements PersonMapper')

        and: '@Generated annotation with correct value'
        content.contains('@Generated("io.github.joke.percolate")')

        and: 'no java.lang. FQN references in non-import lines'
        def nonImportLines = content.readLines().findAll { !it.trim().startsWith('import ') }
        nonImportLines.every { !it.contains('java.lang.') }

        and: 'public no-arg constructor exists'
        content.contains('public PersonMapperImpl()')

        and: 'overridden map method returns the expected constructor call'
        content.contains('@Override')
        content.contains('public Human map(Person person)')
        content.contains('return new Human(person.getFirstName())')
    }

    def 'unmatchable @Map directive yields validation diagnostic and no generated source'() {
        given:
        def person = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.SkipPerson',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class SkipPerson {',
                '    private final String firstName;',
                '    public SkipPerson(final String firstName) { this.firstName = firstName; }',
                '    public String getFirstName() { return firstName; }',
                '}')

        def human = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.SkipHuman',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class SkipHuman {',
                '    private final String firstName;',
                '    public SkipHuman(final String firstName) { this.firstName = firstName; }',
                '    public String getFirstName() { return firstName; }',
                '}')

        def skipMapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.SkipMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface SkipMapper {',
                '    @Map(target = "nonExistentField", source = "firstName")',
                '    SkipHuman map(SkipPerson person);',
                '}')

        when:
        Compilation compilation = PercolateCompiler.compile(person, human, skipMapper)

        then: 'compilation failed at the @Mapper declaration site, not at a generated Impl'
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        !errors.empty
        errors.every { it.source != null && it.source.name.endsWith('SkipMapper.java') }
    }
}
