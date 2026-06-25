package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

@Tag('integration')
class SourcePathChainEndToEndSpec extends Specification {

    def 'a three-segment source path renders as a forward-descended accessor chain'() {
        given: 'Address.getStreet(), Person.getAddress(), and a single-field Human'
        def address = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Address',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class Address {',
                '    private final String street;',
                '    public Address(final String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')

        def person = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Person',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class Person {',
                '    private final Address address;',
                '    public Person(final Address address) { this.address = address; }',
                '    public Address getAddress() { return address; }',
                '}')

        def human = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Human',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class Human {',
                '    private final String street;',
                '    public Human(final String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.AddressMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface AddressMapper {',
                '    @Map(target = "street", source = "person.address.street")',
                '    Human map(Person person);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(address, person, human, mapper)

        then: 'compilation succeeded'
        compilation.errors().empty

        and: 'the two accessor hops chain forward from the parameter, with no assembly misfire on the source-path values'
        def content = compilation
                .generatedSourceFile('io.github.joke.percolate.processor.test.fixtures.AddressMapperImpl')
                .get().getCharContent(true).toString()
        content.contains('return new Human(person.getAddress().getStreet())')
    }
}
