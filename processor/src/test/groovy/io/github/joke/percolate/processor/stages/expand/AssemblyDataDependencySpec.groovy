package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.test.TestGroups

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.AssemblyStrategy
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import java.util.stream.Stream

/**
 * Assembly (design D7) resolves by data dependency, not by a dedicated assembly-first phase. The deleted
 * {@code ResolveTargetChainsPhase} pre-ordered N-ary target construction; now an {@link AssemblyStrategy} emits an
 * ordinary multi-slot {@code BOUNDARY} step whose slots bind <em>by name</em> to the pre-seeded target leaves that
 * already exist in the assembly group. The construction commits against whatever leaves the seed produced — no
 * fresh inputs are allocated for the bound members and no phase ordering is consulted — and each leaf then resolves
 * on its own in subsequent fixed-point passes.
 */
@Tag('unit')
class AssemblyDataDependencySpec extends Specification {

    private static final String SEED_FQN = 'io.github.joke.percolate.processor.stages.seed.SeedStage'
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def ctx = HarnessResolveCtx.create()
    def state = new ExpansionStateImpl(
            graph,
            new Applier(new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())))

    def 'an assembly BOUNDARY binds its slots by name to the pre-seeded target leaves, allocating no fresh inputs'() {
        given: 'a pre-seeded assembly group: a tgt[] root over its child target leaves name/age'
        def root = target('', TypeUniverse.STRING)
        def nameLeaf = target('name', TypeUniverse.STRING)
        def ageLeaf = target('age', TypeUniverse.INT)
        graph.addNode(root)
        graph.addNode(nameLeaf)
        graph.addNode(ageLeaf)
        def group = TestGroups.of(root, [nameLeaf, ageLeaf], SEED_FQN, [].toSet(), graph)
        assert GroupShapes.isAssembly(group)

        and: 'a constructor-style assembly strategy emitting a 2-slot BOUNDARY whose slot names are the members'
        def constructor = { f, c ->
            Stream.of(ExpansionStep.boundary(
                    [new Slot('name', TypeUniverse.STRING, Weights.STEP, null), new Slot('age', TypeUniverse.INT, Weights.STEP, null)],
                    TypeUniverse.STRING,
                    EDGE_NOOP,
                    Weights.STEP))
        } as AssemblyStrategy
        def matcher = new FrontierMatcher([constructor], new InputAllocator(ctx), ctx)

        when:
        def bundles = matcher.matchAssembly(root, group, state)

        then: 'one assembly bundle, and no fresh input nodes were minted — the slots reused the pre-seeded leaves'
        bundles.size() == 1
        bundles[0].deltas.findAll { it instanceof AddNode }.empty

        and: 'the assembly edges run from exactly the pre-seeded leaves into the root (by data dependency)'
        def addEdges = bundles[0].deltas.findAll { it instanceof AddEdge }
        addEdges.every { it.to.is(root) }
        addEdges*.from.toSet() == [nameLeaf, ageLeaf].toSet()

        and: 'the opened sub-group is rooted at the assembly root over those same leaves as its slots'
        def addGroup = bundles[0].deltas.find { it instanceof AddGroup }
        addGroup.root.is(root)
        addGroup.inputs.toSet() == [nameLeaf, ageLeaf].toSet()
    }

    def 'a constructor whose parameter names do not equal the declared children opens no sub-group'() {
        given: 'a pre-seeded assembly umbrella whose declared children are name/age'
        def root = target('', TypeUniverse.STRING)
        def nameLeaf = target('name', TypeUniverse.STRING)
        def ageLeaf = target('age', TypeUniverse.INT)
        graph.addNode(root)
        graph.addNode(nameLeaf)
        graph.addNode(ageLeaf)
        def group = TestGroups.of(root, [nameLeaf, ageLeaf], SEED_FQN, [].toSet(), graph)

        and: 'an assembly strategy whose constructor parameter-name set differs from the declared children'
        def constructor = { f, c ->
            Stream.of(ExpansionStep.boundary(
                    [new Slot('name', TypeUniverse.STRING, Weights.STEP, null),
                     new Slot('age', TypeUniverse.INT, Weights.STEP, null),
                     new Slot('country', TypeUniverse.STRING, Weights.STEP, null)],
                    TypeUniverse.STRING,
                    EDGE_NOOP,
                    Weights.STEP))
        } as AssemblyStrategy
        def matcher = new FrontierMatcher([constructor], new InputAllocator(ctx), ctx)

        when:
        def bundles = matcher.matchAssembly(root, group, state)

        then: 'the name-set-equality gate rejects the constructor: no bundle, no node minted'
        bundles.empty
    }

    private Node target(final String path, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), new TargetLocation(TargetPath.of(path)), scope)
    }
}
