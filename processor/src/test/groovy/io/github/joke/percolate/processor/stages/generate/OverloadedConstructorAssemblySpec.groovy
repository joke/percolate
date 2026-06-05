package io.github.joke.percolate.processor.stages.generate

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.PercolateProcessor
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.Diagnostic

/**
 * Regression for type-divergent overloaded constructors: {@code HumanAddr(int number, String street)} and
 * {@code HumanAddr(long number, String street)} share the declared-child name set {number, street} but disagree on
 * {@code number}'s type. The seed pre-creates one untyped {@code tgt[number]} leaf; over-emit binds both
 * constructors to that single leaf and they race to pin {@code int} vs {@code long}, so no constructor cleanly
 * SATs and the return-root drops out of the plan ({@code no return-root TargetLocation node in scope}). The fix
 * lets each constructor bind its own per-(name,type) typed leaf fed from the one shared source value, so the
 * exact-type ({@code int}) constructor wins by cost.
 */
@Tag('integration')
class OverloadedConstructorAssemblySpec extends Specification {

    def 'type-divergent overloaded constructors select the exact-type constructor and compile'() {
        given:
        def personAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.PersonAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class PersonAddr {',
                '    private final String street;',
                '    private final int number;',
                '    public PersonAddr(final String street, final int number) {',
                '        this.street = street;',
                '        this.number = number;',
                '    }',
                '    public String getStreet() { return street; }',
                '    public int getNumber() { return number; }',
                '}')

        def humanAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.HumanAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class HumanAddr {',
                '    private final String street;',
                '    private final long number;',
                '    public HumanAddr(final long number, final String street) {',
                '        this.number = number;',
                '        this.street = street;',
                '    }',
                '    public HumanAddr(final int number, final String street) {',
                '        this.number = number;',
                '        this.street = street;',
                '    }',
                '    public String getStreet() { return street; }',
                '    public long getNumber() { return number; }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.AddrMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface AddrMapper {',
                '    @Map(target = "street", source = "address.street")',
                '    @Map(target = "number", source = "address.number")',
                '    HumanAddr map(PersonAddr address);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(personAddr, humanAddr, mapper)

        then: 'compilation succeeded'
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        errors.empty

        and: 'generated source file exists'
        def generatedSource = compilation
                .generatedSourceFile('io.github.joke.percolate.processor.test.fixtures.AddrMapperImpl')
        generatedSource.present

        and: 'the exact-type (int) constructor is chosen: number passed without a widening cast'
        def content = generatedSource.get().getCharContent(true).toString()
        content.contains('new HumanAddr(')
        content.contains('address.getNumber()')
        content.contains('address.getStreet()')
    }

    def 'a no-arg constructor never wins over the all-args constructor when fields are declared'() {
        given:
        def personAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.NaPersonAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class NaPersonAddr {',
                '    private final String street;',
                '    public NaPersonAddr(final String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')

        def humanAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.NaHumanAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class NaHumanAddr {',
                '    private final String street;',
                '    public NaHumanAddr() { this.street = ""; }',
                '    public NaHumanAddr(final String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.NaMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface NaMapper {',
                '    @Map(target = "street", source = "address.street")',
                '    NaHumanAddr map(NaPersonAddr address);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(personAddr, humanAddr, mapper)

        then: 'compilation succeeded'
        compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }.empty

        and: 'the all-args constructor is used, never the empty no-arg one'
        def content = compilation
                .generatedSourceFile('io.github.joke.percolate.processor.test.fixtures.NaMapperImpl')
                .get().getCharContent(true).toString()
        content.contains('new NaHumanAddr(address.getStreet())')
        !content.contains('new NaHumanAddr()')
    }

    def 'an unsatisfiable assembly (no name-matching constructor) is diagnosed, not an internal crash'() {
        given:
        def personAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.MmPersonAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class MmPersonAddr {',
                '    private final String street;',
                '    public MmPersonAddr(final String street) { this.street = street; }',
                '    public String getStreet() { return street; }',
                '}')

        def humanAddr = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.MmHumanAddr',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'public final class MmHumanAddr {',
                '    private final String street;',
                '    private final int number;',
                '    public MmHumanAddr(final int number, final String street) {',
                '        this.number = number;',
                '        this.street = street;',
                '    }',
                '    public String getStreet() { return street; }',
                '    public int getNumber() { return number; }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.MmMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface MmMapper {',
                '    @Map(target = "street", source = "address.street")',
                '    MmHumanAddr map(MmPersonAddr address);',
                '}')

        when:
        Compilation compilation = Compiler.javac()
                .withProcessors(new PercolateProcessor())
                .compile(personAddr, humanAddr, mapper)

        then: 'an error is reported at the mapper declaration, not an internal IllegalStateException'
        def errors = compilation.diagnostics().findAll { it.kind == Diagnostic.Kind.ERROR }
        !errors.empty
        errors.every { !it.getMessage(null).contains('IllegalStateException') }
        errors.every { it.source != null && it.source.name.endsWith('MmMapper.java') }
    }
}
