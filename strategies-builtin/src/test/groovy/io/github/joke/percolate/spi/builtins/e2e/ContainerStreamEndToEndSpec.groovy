package io.github.joke.percolate.spi.builtins.e2e

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.test.PercolateCompiler
import spock.lang.Specification
import spock.lang.Tag

/**
 * The {@code mapHuman} regression guard (design D7, value-operation-graph §10): a cross-kind, mismatched-nesting,
 * drop-empties container conversion {@code List<Optional<A>> -> Optional<Set<B>>}. The fused same-kind element-map
 * model could not phrase it ("no producer in the graph"); the stream-intermediate model composes it from
 * single-kind / kind-free operations: {@code wrap <- collect <- map[A->B] <- flatMap[Optional->Stream] <- iterate(List)}.
 * The bar is compiles + semantically equivalent, not byte-identical.
 */
@Tag('integration')
class ContainerStreamEndToEndSpec extends Specification {

    def 'List<Optional<A>> to Optional<Set<B>> composes iterate/flatMap/map/collect/wrap and compiles'() {
        given:
        def beast = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Beast',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import java.util.List;',
                'import java.util.Optional;',
                '',
                'public final class Beast {',
                '    private final List<Optional<Paw>> paws;',
                '    public Beast(final List<Optional<Paw>> paws) { this.paws = paws; }',
                '    public List<Optional<Paw>> getPaws() { return paws; }',
                '    public static final class Paw {',
                '        private final String name;',
                '        public Paw(final String name) { this.name = name; }',
                '        public String getName() { return name; }',
                '    }',
                '}')

        def creature = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.Creature',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import java.util.Optional;',
                'import java.util.Set;',
                '',
                'public final class Creature {',
                '    private final Optional<Set<Claw>> paws;',
                '    public Creature(final Optional<Set<Claw>> paws) { this.paws = paws; }',
                '    public Optional<Set<Claw>> getPaws() { return paws; }',
                '    public static final class Claw {',
                '        private final String name;',
                '        public Claw(final String name) { this.name = name; }',
                '        public String getName() { return name; }',
                '    }',
                '}')

        def mapper = JavaFileObjects.forSourceLines(
                'io.github.joke.percolate.processor.test.fixtures.BeastMapper',
                'package io.github.joke.percolate.processor.test.fixtures;',
                '',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '',
                '@Mapper',
                'public interface BeastMapper {',
                '    @Map(target = "paws", source = "beast.paws")',
                '    Creature map(Beast beast);',
                '',
                '    @Map(target = "name", source = "paw.name")',
                '    Creature.Claw mapPaw(Beast.Paw paw);',
                '}')

        when:
        Compilation compilation = PercolateCompiler.compile(beast, creature, mapper)

        then: 'the generated mapper compiles'
        compilation.errors().empty

        and: 'the generated source exists'
        def generated = compilation
                .generatedSourceFile('io.github.joke.percolate.processor.test.fixtures.BeastMapperImpl')
        generated.present

        and: 'the body is the single-kind stream chain, every stage threaded onto its operand'
        def body = generated.get().getCharContent(true).toString()
        def map = body.readLines().find { it.contains('return new Creature(') }
        map != null
        map.contains('.stream()')                 // iterate(List)
        map.contains('.flatMap(')                 // drop-empties over Optional
        map.contains('.stream()')                 // iterate(Optional) inside the flatMap
        map.contains('.map(')                     // element transform Paw -> Claw
        map.contains('mapPaw(')                   // ...delegated to the nested mapper method
        map.contains('.collect(')                 // collect into the Set
        map.contains('toSet()')
        map.contains('Optional.ofNullable(')      // wrap into the Optional target

        and: 'the element method assembles a Claw from the Paw'
        body.contains('public Creature.Claw mapPaw(Beast.Paw paw)')
        body.contains('new Creature.Claw(paw.getName())')
    }
}
