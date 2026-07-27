package io.github.joke.percolate.spi.builtins

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

import javax.tools.JavaFileObject

/**
 * Compile-testing coverage for {@code @Map}'s own shape rules, now owned by {@link MapDirectiveReader} rather than
 * by a core stage (design D7 of change {@code decouple-engine-from-strategy-semantics}, which deletes
 * {@code ValidateMappingShapeStage}): a violated rule declines to {@code bind} and {@code reject}s the declaration,
 * so the contradiction reaches the author as a positioned compile error naming the rule — never a silently dropped
 * binding, and never only the generic "no plan" line.
 *
 * <p>A rejection rather than a {@code Constraint} for a reason these very fixtures found: a constraint refuses
 * candidates, and a malformed declaration usually leaves nothing able to offer one, so the rule the author broke
 * was swallowed behind an unrelated self-call refusal at the root.
 *
 * <p>The reader itself is {@code @CoverageIgnore} (it consumes {@code javax.lang.model} directly, like
 * {@code CallableMethodIndexer}), so this compile-based layer is where its rules are pinned.
 */
@Tag('integration')
class MapShapeNegativeSpec extends Specification {

    def 'declaring both a source and a constant fails the compile, naming the exclusion'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, VIEW, BOTH_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any {
            it.getMessage(null).contains("@Map declares both 'source' and 'constant'; they are mutually exclusive")
        }
    }

    def 'declaring neither a source nor a constant fails the compile, naming the requirement'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, VIEW, NEITHER_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any {
            it.getMessage(null).contains("@Map must declare a 'source' or a 'constant'")
        }
    }

    def 'a defaultValue without a source fails the compile, naming the dependency'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, VIEW, DEFAULT_WITHOUT_SOURCE_MAPPER)

        then:
        !compilation.errors().empty
        compilation.errors().any {
            it.getMessage(null).contains("@Map 'defaultValue' requires a 'source'")
        }
    }

    def 'the rejection is reported in place of the generic no-plan line'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, VIEW, BOTH_MAPPER)

        then:
        !compilation.errors().any { it.getMessage(null).contains('no plan for') }
    }

    def 'a well-shaped mapping compiles'() {
        when:
        Compilation compilation = PercolateCompiler.compile(PERSON, VIEW, VALID_MAPPER)

        then:
        compilation.errors().empty
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    private static final JavaFileObject PERSON = JavaFileObjects.forSourceLines(
            'examples.mapshape.Person',
            'package examples.mapshape;',
            'public class Person {',
            '    private final String name;',
            '    public Person(String name) { this.name = name; }',
            '    public String getName() { return name; }',
            '}')

    private static final JavaFileObject VIEW = JavaFileObjects.forSourceLines(
            'examples.mapshape.View',
            'package examples.mapshape;',
            'public class View {',
            '    private final String label;',
            '    public View(String label) { this.label = label; }',
            '    public String getLabel() { return label; }',
            '}')

    private static final JavaFileObject BOTH_MAPPER = JavaFileObjects.forSourceLines(
            'examples.mapshape.BothMapper',
            'package examples.mapshape;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface BothMapper {',
            '    @Map(target = "label", source = "person.name", constant = "fixed")',
            '    View map(Person person);',
            '}')

    private static final JavaFileObject NEITHER_MAPPER = JavaFileObjects.forSourceLines(
            'examples.mapshape.NeitherMapper',
            'package examples.mapshape;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface NeitherMapper {',
            '    @Map(target = "label")',
            '    View map(Person person);',
            '}')

    private static final JavaFileObject DEFAULT_WITHOUT_SOURCE_MAPPER = JavaFileObjects.forSourceLines(
            'examples.mapshape.DefaultWithoutSourceMapper',
            'package examples.mapshape;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface DefaultWithoutSourceMapper {',
            '    @Map(target = "label", constant = "fixed", defaultValue = "fallback")',
            '    View map(Person person);',
            '}')

    private static final JavaFileObject VALID_MAPPER = JavaFileObjects.forSourceLines(
            'examples.mapshape.ValidMapper',
            'package examples.mapshape;',
            'import io.github.joke.percolate.Map;',
            'import io.github.joke.percolate.Mapper;',
            '@Mapper',
            'public interface ValidMapper {',
            '    @Map(target = "label", source = "person.name")',
            '    View map(Person person);',
            '}')
}
