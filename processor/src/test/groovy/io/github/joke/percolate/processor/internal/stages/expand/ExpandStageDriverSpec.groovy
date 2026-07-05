package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.Location
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.model.GoalSpec
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.FakeResolveCtx
import io.github.joke.percolate.processor.test.FakeType
import io.github.joke.percolate.spi.DescendDemand
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ProduceDemand
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import java.util.stream.Stream

/**
 * The expansion driver (design D5/D6) seam, unit-tested directly: a constructed {@link MapperShape} is seeded and
 * expanded over a fresh {@link MapperGraph} with stub strategies, and the assertions read the resulting graph
 * structure. The cases isolate each driver path — self-seeding, producer landing, the three {@link Port.Sourcing}
 * modes, the whole-parameter self-call guard, and the directive-pinned forward source descent.
 *
 * <p>Unit-tested mock-only (change {@code type-query-seam}): a {@link FakeResolveCtx} answers every seam question
 * structurally, {@link FakeElements} stand in for the compiled method/parameter {@code Element}s, and the
 * nullability resolver is a fixed stub — no javac, no compiled fixture classes.
 */
@Tag('unit')
class ExpandStageDriverSpec extends Specification {

    @Shared ResolveCtx resolveCtx = new FakeResolveCtx()
    @Shared NullabilityResolver resolver = { TypeMirror type, def scope -> Nullability.NON_NULL } as NullabilityResolver
    @Shared OperationCodegen codegen = { inc -> CodeBlock.of('x') } as OperationCodegen

    @Shared TypeMirror personType = FakeType.declared('Person')
    @Shared TypeMirror humanType = FakeType.declared('Human')
    @Shared TypeMirror stringType = FakeType.declared('String')
    @Shared TypeMirror integerType = FakeType.declared('Integer')

    // ---- self-seeding -----------------------------------------------------------------------------------------

    def 'self-seeds exactly one return-root Value for the single abstract method, in that method scope'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()

        when:
        driver(graph).seedAndExpand(shape(method))

        then: 'one return root, owned by the method scope'
        def roots = graph.returnRoots().toList()
        roots.size() == 1
        def root = roots[0]
        root.scope == new MethodScope(method)
        graph.valuesIn(new MethodScope(method)).toList() == roots

        and: 'it is the empty-path target location flagged as the return root'
        root.loc instanceof TargetLocation
        root.loc.path.toString() == ''
        root.loc.returnRoot

        and: 'typed and nulled from the method return, through the resolver'
        resolveCtx.isSameType(root.type.get(), humanType)
        root.nullness.get() == Nullability.NON_NULL
    }

    def 'with no strategies no producer lands — the seed drains to a graph of one Value and zero operations'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()

        when:
        driver(graph).seedAndExpand(shape(method))

        then:
        graph.values().count() == 1
        graph.operations().count() == 0
    }

    // ---- producer landing + port sourcing ---------------------------------------------------------------------

    def 'lands a producer as the return-root producer and mints one child-target demand per SUBTARGET port'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.subTarget('firstName', stringType, Nullability.NON_NULL),
                 Port.subTarget('lastName', stringType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'the producer is the return root\'s sole producer, outputting the root'
        def root = graph.returnRoots().toList()[0]
        def producers = graph.producersOf(root).toList()
        producers.size() == 1
        def operation = producers[0]
        operation.label == 'new'
        graph.outputOf(operation).get() == root

        and: 'each SUBTARGET port minted a fresh child-target demand at its child location — root + 2 children'
        graph.portSourcesOf(operation).collect { it.loc.path.toString() }.toSet() == ['firstName', 'lastName'].toSet()
        graph.values().count() == 3
        graph.operations().count() == 1
    }

    def 'a REUSE port binds the matching method parameter, materialised on demand as a LEAF source'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy', codegen, Weights.COPY,
                [Port.reuse('src', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'the REUSE port bound the method parameter, materialised as a single-segment LEAF source'
        def operation = graph.operations().toList()[0]
        def source = graph.portSourcesOf(operation).toList()[0]
        source.loc instanceof SourceLocation
        source.loc.path.toString() == 'person'
        source.loc.role() == Location.Role.LEAF
        graph.values().count() == 2
    }

    def 'a REUSE port with no in-scope source of its type does not apply — no operation lands'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy', codegen, Weights.COPY,
                [Port.reuse('src', integerType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'no Integer is in scope, so the REUSE producer is never minted'
        graph.operations().count() == 0
        graph.values().count() == 1
    }

    def 'a REUSE_OR_MINT port with no in-scope source mints a fresh intermediate at the output location'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('convert', codegen, Weights.STEP,
                [new Port('in', integerType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'the operation lands; its REUSE_OR_MINT port mints a fresh Integer intermediate at the output location'
        def operation = graph.operations().toList()[0]
        def minted = graph.portSourcesOf(operation).toList()[0]
        resolveCtx.isSameType(minted.type.get(), integerType)
        minted.loc instanceof TargetLocation
        minted.loc.path.toString() == ''
        graph.operations().count() == 1
        graph.values().count() == 2
    }

    // ---- self-call guard wiring (the guard's branch matrix lives in SelfCallGuardSpec) -------------------------

    def 'consults the self-call guard: a whole-parameter self-call is refused and nothing lands'() {
        given:
        def method = mapMethod()
        def graph = new MapperGraph()
        def selfCall = OperationSpec.callOf('map', codegen, Weights.METHOD,
                [Port.reuse('arg', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL, method)

        when:
        driver(graph, [produces(humanType, selfCall)]).seedAndExpand(shape(method))

        then: 'the driver wires the guard in, so the degenerate self-call never lands'
        graph.operations().count() == 0
    }

    // ---- directive-pinned forward source descent --------------------------------------------------------------

    def 'a directive source path materialises the parameter root and descends each accessor segment'() {
        given:
        def method = mapMethod()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def getter = OperationSpec.of('getFirstName', codegen, Weights.STEP_GETTER,
                [new Port('self', personType, Nullability.NON_NULL)],
                stringType, Nullability.NON_NULL)
        def goalSpecs = [(scope): GoalSpec.from([directive('', 'person.firstName')])]

        when:
        new ExpandStage.Driver([reads(personType, 'firstName', getter)], [], resolver, graph, goalSpecs,
                resolveCtx).seedAndExpand(shape(method))

        then: 'forward descent materialised the parameter LEAF and the firstName ACCESS source'
        def sources = graph.values().findAll { it.loc instanceof SourceLocation }
        sources.collect { it.loc.path.toString() }.toSet() == ['person', 'person.firstName'].toSet()

        and: 'one accessor operation reads firstName off the parameter LEAF, yielding an ACCESS source'
        def getterOp = graph.operations().toList().find { it.label == 'getFirstName' }
        getterOp != null
        graph.portSourcesOf(getterOp).toList()[0].loc.path.toString() == 'person'
        def access = graph.outputOf(getterOp).get()
        access.loc.path.toString() == 'person.firstName'
        access.loc.role() == Location.Role.ACCESS

        and: 'the graph holds exactly the root plus the two source-path Values'
        graph.values().count() == 3
    }

    def 'a source feeding two ports of one producer is expanded once, not re-visited'() {
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy2', codegen, Weights.COPY,
                [Port.reuse('a', personType, Nullability.NON_NULL),
                 Port.reuse('b', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when: 'both REUSE ports bind the one parameter source, so it is enqueued twice'
        driver(graph, [produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'the shared parameter source is a single Value; the second visit is a no-op'
        def operation = graph.operations().toList()[0]
        graph.portSourcesOf(operation).distinct().count() == 1
        graph.values().count() == 2
    }

    def 'a nested SUBTARGET port lands at a multi-segment child-target location'() {
        def method = mapMethod()
        def graph = new MapperGraph()
        def outer = OperationSpec.of('newHuman', codegen, Weights.STEP,
                [Port.subTarget('addr', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)
        def inner = OperationSpec.of('newAddr', codegen, Weights.STEP,
                [Port.subTarget('street', stringType, Nullability.NON_NULL)],
                personType, Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType, outer), produces(personType, inner)]).seedAndExpand(shape(method))

        then: 'the inner SUBTARGET mints a demand at the two-segment path addr.street'
        graph.values().collect { it.loc.path?.toString() }.contains('addr.street')
    }

    def 'a directive with an empty source path pins no source'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', '')])]

        when: 'the empty source splits to no segments, so no leaf is pinned'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx).seedAndExpand(shape(method))

        then: 'only the return root exists — no source Value was materialised'
        graph.values().findAll { it.loc instanceof SourceLocation }.empty
    }

    def 'a directive whose source root matches no scope input pins no source'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', 'ghost.firstName')])]

        when: 'materialiseRoot finds no input named "ghost", so descent stops before the first segment'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx).seedAndExpand(shape(method))

        then:
        graph.values().findAll { it.loc instanceof SourceLocation }.empty
    }

    def 'a single-segment directive source materialises just the parameter root'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', 'person')])]

        when: 'a one-segment source needs no accessor descent — the root is the pinned source'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx).seedAndExpand(shape(method))

        then:
        def sources = graph.values().findAll { it.loc instanceof SourceLocation }
        sources.collect { it.loc.path.toString() } == ['person']
    }

    def 'two accessors for one segment over-emit into a single deduped source Value'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def getter = OperationSpec.of('getFirstName', codegen, Weights.STEP_GETTER,
                [new Port('self', personType, Nullability.NON_NULL)],
                stringType, Nullability.NON_NULL)
        def field = OperationSpec.of('firstNameField', codegen, Weights.STEP_GETTER,
                [new Port('self', personType, Nullability.NON_NULL)],
                stringType, Nullability.NON_NULL)
        def goalSpecs = [(scope): GoalSpec.from([directive('', 'person.firstName')])]

        when: 'both accessors read firstName off the parameter — the second reuses the first\'s output Value'
        new ExpandStage.Driver([reads(personType, 'firstName', getter),
                                reads(personType, 'firstName', field)], [], resolver, graph, goalSpecs,
                resolveCtx).seedAndExpand(shape(method))

        then: 'two accessor operations, one shared firstName source Value'
        graph.operations()*.label.toSet() == ['getFirstName', 'firstNameField'].toSet()
        graph.values().findAll { it.loc.path?.toString() == 'person.firstName' }.size() == 1
    }

    def 'duplicate producer specs are deduplicated before landing'() {
        def method = mapMethod()
        def graph = new MapperGraph()
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.subTarget('firstName', stringType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        when: 'two strategies offer the identical spec — dedup drops the second signature'
        driver(graph, [produces(humanType, producer), produces(humanType, producer)]).seedAndExpand(shape(method))

        then: 'the producer lands exactly once'
        graph.operations().count() == 1
    }

    // ---- ExpandStage.run() wiring -----------------------------------------------------------------------------

    def 'run installs a fresh graph on the context and self-seeds the return root'() {
        given:
        def method = mapMethod()
        def ctx = new MapperContext(Stub(TypeElement))
        ctx.shape = new MapperShape(Stub(TypeElement), [method])

        when:
        stage().run(ctx)

        then: 'the stage built the graph, installed it, and seeded the one return root'
        ctx.graph != null
        ctx.graph.returnRoots().toList().size() == 1
    }

    def 'run is a no-op when discovery produced no shape — no graph is installed'() {
        given:
        def ctx = new MapperContext(Stub(TypeElement))

        when:
        stage().run(ctx)

        then:
        ctx.graph == null
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ExecutableElement mapMethod() {
        FakeElements.method('map', humanType, FakeElements.param('person', personType))
    }

    private MapperShape shape(final ExecutableElement method) {
        new MapperShape(Stub(TypeElement), [method])
    }

    private ExpandStage stage() {
        new ExpandStage([], [], Stub(Types), Stub(Elements), resolver)
    }

    /** A producer strategy that offers {@code spec} only for the {@code target} demand, empty for any other. */
    private ExpansionStrategy produces(final TypeMirror target, final OperationSpec spec) {
        new ExpansionStrategy() {
            @Override
            Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx c) {
                c.isSameType(demand.targetType(), target) ? Stream.of(spec) : Stream.empty()
            }
        }
    }

    /** An accessor strategy that offers {@code spec} only for reading {@code segment} off {@code parent}. */
    private ExpansionStrategy reads(final TypeMirror parent, final String segment, final OperationSpec spec) {
        new ExpansionStrategy() {
            @Override
            Stream<OperationSpec> descend(final DescendDemand demand, final ResolveCtx c) {
                c.isSameType(demand.parentType(), parent) && demand.segment() == segment ?
                        Stream.of(spec) : Stream.empty()
            }
        }
    }

    private ExpandStage.Driver driver(final MapperGraph graph, final List<ExpansionStrategy> strategies = []) {
        new ExpandStage.Driver(strategies, [], resolver, graph, [:], resolveCtx)
    }

    /** A {@code @Map(target, source)} directive; the annotation-mirror fields are unused on the descent seam. */
    private MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, null, null, null, null, null, null, null)
    }
}
