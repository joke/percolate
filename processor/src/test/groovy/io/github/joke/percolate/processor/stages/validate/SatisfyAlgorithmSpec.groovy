package io.github.joke.percolate.processor.stages.validate;

import io.github.joke.percolate.processor.graph.*;
import io.github.joke.percolate.spi.test.TypeUniverse;
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout
import spock.lang.Unroll

import java.util.HashSet

@Tag('unit')
@Timeout(30)
class SatisfyAlgorithmSpec extends Specification {

    def 'source-parameter node is SAT (base case)'() {
        given:
        def graph = new MapperGraph()
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
        graph.addNode(source)
        def satisfySearch = new SatisfySearch(graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(source, visited)

        then:
        result.isSat()
    }

    def 'single REALISED hop from a parameter is SAT'() {
        given:
        def graphData = buildGraphWithRealisedPath()
        def source = graphData.graph.nodes().findFirst().orElse(null)
        def target = graphData.graph.edges().filter { it.getKind() == EdgeKind.REALISED }.findFirst().orElse(null)?.getTo()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(target, visited)

        then:
        result.isSat()
    }

    def 'missing producer is UNSAT with no-incoming explanation'() {
        given:
        def graph = new MapperGraph()
        def target = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('out')), new TestScope('m()'), Optional.empty())
        graph.addNode(target)
        def satisfySearch = new SatisfySearch(graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(target, visited)

        then:
        result.isUnsat()
        result.message().contains('no producer')
    }

    def 'unsatisfiable SUB_SEED promise propagates UNSAT'() {
        given:
        def graphData = buildGraphWithSubSeedCycle()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(graphData.b, visited)

        then:
        result.isUnsat()
    }

    @Unroll
    def '#scenario satisfies the algorithm requirement'() {
        expect:
        result.outcome() == expected

        where:
        scenario                                    | result            | expected
        'base case (source parameter)'              | baseCaseResult()  | SatisfyOutcome.SAT
        'single hop SAT'                            | singleHopSat()    | SatisfyOutcome.SAT
        'missing producer UNSAT'                    | missingProducer() | SatisfyOutcome.UNSAT
        'cycle UNSAT'                               | cycleResult()     | SatisfyOutcome.UNSAT
    }

    def 'first SAT among parallel REALISED edges wins'() {
        given:
        def graphData = buildGraphWithParallelEdges()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(graphData.target, visited)

        then:
        result.isSat()
    }

    def 'deepest miss is reported when all alternatives are UNSAT'() {
        given:
        def graphData = buildGraphWithDeepestMiss()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(graphData.target, visited)

        then:
        result.isUnsat()
        result.depth() >= 2
    }

    def 'tie-broken by strategyClassFqn when depths are equal'() {
        given:
        def graphData = buildGraphWithTieBreak()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()

        when:
        def result = satisfySearch.satisfy(graphData.target, visited)

        then:
        result.isUnsat()
        result.strategyFqn() == 'com.a.A'
    }

    def 'visited set is per-invocation'() {
        given:
        def graph = new MapperGraph()
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
        def target1 = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('out1')), new TestScope('m()'), Optional.empty())
        def target2 = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('out2')), new TestScope('m()'), Optional.empty())
        graph.addNode(source)
        graph.addNode(target1)
        graph.addNode(target2)
        def satisfySearch = new SatisfySearch(graph)
        def visited1 = new HashSet<Node>()

        when:
        def result1 = satisfySearch.satisfy(target1, visited1)
        def visited2 = new HashSet<Node>()
        def result2 = satisfySearch.satisfy(target2, visited2)

        then:
        result1.isUnsat()
        result2.isUnsat()
    }

    private static SatisfyResult baseCaseResult() {
        def graph = new MapperGraph()
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
        graph.addNode(source)
        def satisfySearch = new SatisfySearch(graph)
        def visited = new HashSet<Node>()
        satisfySearch.satisfy(source, visited)
    }

    private static SatisfyResult singleHopSat() {
        def graphData = buildGraphWithRealisedPath()
        def source = graphData.graph.nodes().findFirst().orElse(null)
        def target = graphData.graph.edges().filter { it.getKind() == EdgeKind.REALISED }.findFirst().orElse(null)?.getTo()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()
        satisfySearch.satisfy(target, visited)
    }

    private static SatisfyResult missingProducer() {
        def graph = new MapperGraph()
        def target = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('out')), new TestScope('m()'), Optional.empty())
        graph.addNode(target)
        def satisfySearch = new SatisfySearch(graph)
        def visited = new HashSet<Node>()
        satisfySearch.satisfy(target, visited)
    }

    private static SatisfyResult cycleResult() {
        def graphData = buildGraphWithSubSeedCycle()
        def satisfySearch = new SatisfySearch(graphData.graph)
        def visited = new HashSet<Node>()
        satisfySearch.satisfy(graphData.b, visited)
    }

    private static class TestGraphWithRealisedPath {
        final MapperGraph graph
        final Node source
        final Node target

        TestGraphWithRealisedPath() {
            this.graph = new MapperGraph()
            this.source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), new TestScope('m(java.lang.String)'), Optional.empty())
            this.target = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), new TestScope('m(java.lang.String)'), Optional.empty())
            graph.addNode(source)
            graph.addNode(target)
            graph.addEdge(Edge.seedForTest(source, target))
            graph.addEdge(Edge.realised(source, target, 1, Optional.empty(), { _, _ -> }, 'Identity'))
        }
    }

    private static TestGraphWithRealisedPath buildGraphWithRealisedPath() {
        new TestGraphWithRealisedPath()
    }

    private static class TestGraphWithSubSeedCycle {
        final MapperGraph graph
        final Node a
        final Node b

        TestGraphWithSubSeedCycle() {
            this.graph = new MapperGraph()
            def scope = new TestScope('cycle()')
            this.a = new Node(Optional.of(TypeUniverse.INT), new SourceLocation(AccessPath.of('a')), scope, Optional.empty())
            this.b = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('b')), scope, Optional.empty())
            graph.addNode(a)
            graph.addNode(b)
            graph.addEdge(Edge.subSeed(a, b, 'cycle.strategy', Optional.empty()))
            graph.addEdge(Edge.subSeed(b, a, 'cycle.strategy', Optional.empty()))
        }
    }

    private static TestGraphWithSubSeedCycle buildGraphWithSubSeedCycle() {
        new TestGraphWithSubSeedCycle()
    }

    private static class TestGraphWithParallelEdges {
        final MapperGraph graph
        final Node source
        final Node target
        final Node mid

        TestGraphWithParallelEdges() {
            this.graph = new MapperGraph()
            this.source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
            this.target = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('out')), new TestScope('m()'), Optional.empty())
            this.mid = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('mid')), new TestScope('m()'), Optional.empty())
            graph.addNode(source)
            graph.addNode(target)
            graph.addNode(mid)
            graph.addEdge(Edge.seedForTest(source, target))
            graph.addEdge(Edge.realised(mid, target, 1, Optional.empty(), { _, _ -> }, 'com.a.Identity'))
        }
    }

    private static TestGraphWithParallelEdges buildGraphWithParallelEdges() {
        new TestGraphWithParallelEdges()
    }

    private static class TestGraphWithDeepestMiss {
        final MapperGraph graph
        final Node source
        final Node target
        final Node mid1
        final Node mid2
        final Node leaf
        final Node leaf2

        TestGraphWithDeepestMiss() {
            this.graph = new MapperGraph()
            this.source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
            this.target = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('out')), new TestScope('m()'), Optional.empty())
            // E1 path: target <- mid1 (depth 1) <- leaf1 (depth 2 - UNSAT, no incoming edges)
            this.mid1 = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('mid1')), new TestScope('m()'), Optional.empty())
            this.leaf = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('leaf1')), new TestScope('m()'), Optional.empty())
            // E2 path: target <- mid2 (depth 1) <- leaf2 (depth 2) <- leaf3 (depth 3 - UNSAT, no incoming edges)
            this.mid2 = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('mid2')), new TestScope('m()'), Optional.empty())
            this.leaf2 = new Node(Optional.of(TypeUniverse.LONG), new TargetLocation(TargetPath.of('leaf2')), new TestScope('m()'), Optional.empty())
            graph.addNode(source)
            graph.addNode(target)
            graph.addNode(mid1)
            graph.addNode(mid2)
            graph.addNode(leaf)
            graph.addNode(leaf2)
            graph.addEdge(Edge.seedForTest(source, target))
            graph.addEdge(Edge.realised(leaf, mid1, 1, Optional.empty(), { _, _ -> }, 'com.a.A'))
            graph.addEdge(Edge.realised(mid1, target, 1, Optional.empty(), { _, _ -> }, 'com.a.B'))
            graph.addEdge(Edge.realised(leaf2, mid2, 1, Optional.empty(), { _, _ -> }, 'com.a.C'))
            graph.addEdge(Edge.realised(mid2, target, 1, Optional.empty(), { _, _ -> }, 'com.a.D'))
        }
    }

    private static TestGraphWithDeepestMiss buildGraphWithDeepestMiss() {
        new TestGraphWithDeepestMiss()
    }

    private static class TestGraphWithTieBreak {
        final MapperGraph graph
        final Node source
        final Node target
        final Node midA
        final Node midB

        TestGraphWithTieBreak() {
            this.graph = new MapperGraph()
            this.source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), new TestScope('m()'), Optional.empty())
            this.target = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('out')), new TestScope('m()'), Optional.empty())
            // Both midA and midB are TargetLocation with no incoming edges (depth 1 UNSAT)
            // Tie-break should pick com.a.A (lexicographically earlier)
            this.midA = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('midA')), new TestScope('m()'), Optional.empty())
            this.midB = new Node(Optional.of(TypeUniverse.INT), new TargetLocation(TargetPath.of('midB')), new TestScope('m()'), Optional.empty())
            graph.addNode(source)
            graph.addNode(target)
            graph.addNode(midA)
            graph.addNode(midB)
            graph.addEdge(Edge.seedForTest(source, target))
            graph.addEdge(Edge.realised(midA, target, 1, Optional.empty(), { _, _ -> }, 'com.b.B'))
            graph.addEdge(Edge.realised(midB, target, 1, Optional.empty(), { _, _ -> }, 'com.a.A'))
        }
    }

    private static TestGraphWithTieBreak buildGraphWithTieBreak() {
        new TestGraphWithTieBreak()
    }

    private static final class TestScope implements Scope {
        private final String name
        TestScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}
