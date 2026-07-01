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
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.DescendDemand
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ProduceDemand
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

/**
 * The expansion driver (design D5/D6) seam, unit-tested directly: a constructed {@link MapperShape} (its abstract
 * methods read off a compiled fixture via {@link TypeUniverse}, no {@code @Mapper} compile) is seeded and expanded
 * over a fresh {@link MapperGraph} with stub strategies, and the assertions read the resulting graph structure. The
 * cases isolate each driver path — self-seeding, producer landing, the three {@link Port.Sourcing} modes, the
 * whole-parameter self-call guard, and the directive-pinned forward source descent.
 */
@Tag('unit')
@Isolated // shares the static TypeUniverse javac; must not run concurrently with other fixture specs (race → flaky pitest)
class ExpandStageDriverSpec extends Specification {

    @Shared ResolveCtx resolveCtx = HarnessResolveCtx.create()
    @Shared JspecifyNullabilityResolver resolver =
            new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    @Shared OperationCodegen codegen = { inc -> CodeBlock.of('x') } as OperationCodegen

    // Prime the fixture closures single-threaded before any driver run: TypeUniverse fills javac symbols lazily and
    // of() follows the type's inheritance/nesting graph only, not its methods' parameter/return types — so the
    // method's Person/Human types must be forced up front or a mid-traversal "Filling X during Y" assertion fires.
    @Shared TypeElement personType = TypeUniverse.of(Person)
    @Shared TypeElement humanType = TypeUniverse.of(Human)
    @Shared TypeElement personMapperType = TypeUniverse.of(PersonMapper)

    // ---- self-seeding -----------------------------------------------------------------------------------------

    def 'self-seeds exactly one return-root Value for the single abstract method, in that method scope'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()

        when:
        driver(graph).seedAndExpand(new MapperShape(personMapperType, [method]))

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

        and: 'typed and nulled from the method return: a @NullMarked Human is non-null'
        resolveCtx.types().isSameType(root.type.get(), humanType.asType())
        root.nullness.get() == Nullability.NON_NULL
    }

    def 'with no strategies no producer lands — the seed drains to a graph of one Value and zero operations'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()

        when:
        driver(graph).seedAndExpand(new MapperShape(personMapperType, [method]))

        then:
        graph.values().count() == 1
        graph.operations().count() == 0
    }

    // ---- producer landing + port sourcing ---------------------------------------------------------------------

    def 'lands a producer as the return-root producer and mints one child-target demand per SUBTARGET port'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.subTarget('firstName', TypeUniverse.STRING, Nullability.NON_NULL),
                 Port.subTarget('lastName', TypeUniverse.STRING, Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

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
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy', codegen, Weights.COPY,
                [Port.reuse('src', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the REUSE port bound the method parameter, materialised as a single-segment LEAF source'
        def operation = graph.operations().toList()[0]
        def source = graph.portSourcesOf(operation).toList()[0]
        source.loc instanceof SourceLocation
        source.loc.path.toString() == method.parameters[0].simpleName.toString()
        source.loc.role() == Location.Role.LEAF
        graph.values().count() == 2
    }

    def 'a REUSE port with no in-scope source of its type does not apply — no operation lands'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy', codegen, Weights.COPY,
                [Port.reuse('src', TypeUniverse.INTEGER, Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'no Integer is in scope, so the REUSE producer is never minted'
        graph.operations().count() == 0
        graph.values().count() == 1
    }

    def 'a REUSE_OR_MINT port with no in-scope source mints a fresh intermediate at the output location'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('convert', codegen, Weights.STEP,
                [new Port('in', TypeUniverse.INTEGER, Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the operation lands; its REUSE_OR_MINT port mints a fresh Integer intermediate at the output location'
        def operation = graph.operations().toList()[0]
        def minted = graph.portSourcesOf(operation).toList()[0]
        resolveCtx.types().isSameType(minted.type.get(), TypeUniverse.INTEGER)
        minted.loc instanceof TargetLocation
        minted.loc.path.toString() == ''
        graph.operations().count() == 1
        graph.values().count() == 2
    }

    // ---- self-call guard wiring (the guard's branch matrix lives in SelfCallGuardSpec) -------------------------

    def 'consults the self-call guard: a whole-parameter self-call is refused and nothing lands'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def selfCall = OperationSpec.callOf('map', codegen, Weights.METHOD,
                [Port.reuse('arg', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL, method)

        when:
        driver(graph, [produces(humanType.asType(), selfCall)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the driver wires the guard in, so the degenerate self-call never lands'
        graph.operations().count() == 0
    }

    // ---- directive-pinned forward source descent --------------------------------------------------------------

    def 'a directive source path materialises the parameter root and descends each accessor segment'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def param = method.parameters[0].simpleName.toString()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def getter = OperationSpec.of('getFirstName', codegen, Weights.STEP_GETTER,
                [new Port('self', personType.asType(), Nullability.NON_NULL)],
                TypeUniverse.STRING, Nullability.NON_NULL)
        def goalSpecs = [(scope): GoalSpec.from([directive('', param + '.firstName')])]

        when:
        new ExpandStage.Driver([reads(personType.asType(), 'firstName', getter)], [], resolver, graph, goalSpecs,
                resolveCtx).seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'forward descent materialised the parameter LEAF and the firstName ACCESS source'
        def sources = graph.values().findAll { it.loc instanceof SourceLocation }
        sources.collect { it.loc.path.toString() }.toSet() == [param, param + '.firstName'].toSet()

        and: 'one accessor operation reads firstName off the parameter LEAF, yielding an ACCESS source'
        def getterOp = graph.operations().toList().find { it.label == 'getFirstName' }
        getterOp != null
        graph.portSourcesOf(getterOp).toList()[0].loc.path.toString() == param
        def access = graph.outputOf(getterOp).get()
        access.loc.path.toString() == param + '.firstName'
        access.loc.role() == Location.Role.ACCESS

        and: 'the graph holds exactly the root plus the two source-path Values'
        graph.values().count() == 3
    }

    def 'a source feeding two ports of one producer is expanded once, not re-visited'() {
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('copy2', codegen, Weights.COPY,
                [Port.reuse('a', personType.asType(), Nullability.NON_NULL),
                 Port.reuse('b', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when: 'both REUSE ports bind the one parameter source, so it is enqueued twice'
        driver(graph, [produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the shared parameter source is a single Value; the second visit is a no-op'
        def operation = graph.operations().toList()[0]
        graph.portSourcesOf(operation).distinct().count() == 1
        graph.values().count() == 2
    }

    def 'a nested SUBTARGET port lands at a multi-segment child-target location'() {
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def outer = OperationSpec.of('newHuman', codegen, Weights.STEP,
                [Port.subTarget('addr', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)
        def inner = OperationSpec.of('newAddr', codegen, Weights.STEP,
                [Port.subTarget('street', TypeUniverse.STRING, Nullability.NON_NULL)],
                personType.asType(), Nullability.NON_NULL)

        when:
        driver(graph, [produces(humanType.asType(), outer), produces(personType.asType(), inner)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the inner SUBTARGET mints a demand at the two-segment path addr.street'
        graph.values().collect { it.loc.path?.toString() }.contains('addr.street')
    }

    def 'a directive with an empty source path pins no source'() {
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', '')])]

        when: 'the empty source splits to no segments, so no leaf is pinned'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx)
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'only the return root exists — no source Value was materialised'
        graph.values().findAll { it.loc instanceof SourceLocation }.empty
    }

    def 'a directive whose source root matches no scope input pins no source'() {
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', 'ghost.firstName')])]

        when: 'materialiseRoot finds no input named "ghost", so descent stops before the first segment'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx)
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then:
        graph.values().findAll { it.loc instanceof SourceLocation }.empty
    }

    def 'a single-segment directive source materialises just the parameter root'() {
        def method = methodNamed(PersonMapper, 'map')
        def param = method.parameters[0].simpleName.toString()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def goalSpecs = [(scope): GoalSpec.from([directive('', param)])]

        when: 'a one-segment source needs no accessor descent — the root is the pinned source'
        new ExpandStage.Driver([], [], resolver, graph, goalSpecs, resolveCtx)
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then:
        def sources = graph.values().findAll { it.loc instanceof SourceLocation }
        sources.collect { it.loc.path.toString() } == [param]
    }

    def 'two accessors for one segment over-emit into a single deduped source Value'() {
        def method = methodNamed(PersonMapper, 'map')
        def param = method.parameters[0].simpleName.toString()
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def getter = OperationSpec.of('getFirstName', codegen, Weights.STEP_GETTER,
                [new Port('self', personType.asType(), Nullability.NON_NULL)],
                TypeUniverse.STRING, Nullability.NON_NULL)
        def field = OperationSpec.of('firstNameField', codegen, Weights.STEP_GETTER,
                [new Port('self', personType.asType(), Nullability.NON_NULL)],
                TypeUniverse.STRING, Nullability.NON_NULL)
        def goalSpecs = [(scope): GoalSpec.from([directive('', param + '.firstName')])]

        when: 'both accessors read firstName off the parameter — the second reuses the first\'s output Value'
        new ExpandStage.Driver([reads(personType.asType(), 'firstName', getter),
                                reads(personType.asType(), 'firstName', field)], [], resolver, graph, goalSpecs,
                resolveCtx).seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'two accessor operations, one shared firstName source Value'
        graph.operations()*.label.toSet() == ['getFirstName', 'firstNameField'].toSet()
        graph.values().findAll { it.loc.path?.toString() == param + '.firstName' }.size() == 1
    }

    def 'duplicate producer specs are deduplicated before landing'() {
        def method = methodNamed(PersonMapper, 'map')
        def graph = new MapperGraph()
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.subTarget('firstName', TypeUniverse.STRING, Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        when: 'two strategies offer the identical spec — dedup drops the second signature'
        driver(graph, [produces(humanType.asType(), producer), produces(humanType.asType(), producer)])
                .seedAndExpand(new MapperShape(personMapperType, [method]))

        then: 'the producer lands exactly once'
        graph.operations().count() == 1
    }

    // ---- ExpandStage.run() wiring -----------------------------------------------------------------------------

    def 'run installs a fresh graph on the context and self-seeds the return root'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def ctx = new MapperContext(personMapperType)
        ctx.shape = new MapperShape(personMapperType, [method])

        when:
        stage().run(ctx)

        then: 'the stage built the graph, installed it, and seeded the one return root'
        ctx.graph != null
        ctx.graph.returnRoots().toList().size() == 1
    }

    def 'run is a no-op when discovery produced no shape — no graph is installed'() {
        given:
        def ctx = new MapperContext(personMapperType)

        when:
        stage().run(ctx)

        then:
        ctx.graph == null
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private ExpandStage stage() {
        new ExpandStage([], [], TypeUniverse.types(), TypeUniverse.elements(), resolver)
    }

    /** A producer strategy that offers {@code spec} only for the {@code target} demand, empty for any other. */
    private ExpansionStrategy produces(final TypeMirror target, final OperationSpec spec) {
        new ExpansionStrategy() {
            @Override
            Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx c) {
                c.types().isSameType(demand.targetType(), target) ? Stream.of(spec) : Stream.empty()
            }
        }
    }

    /** An accessor strategy that offers {@code spec} only for reading {@code segment} off {@code parent}. */
    private ExpansionStrategy reads(final TypeMirror parent, final String segment, final OperationSpec spec) {
        new ExpansionStrategy() {
            @Override
            Stream<OperationSpec> descend(final DescendDemand demand, final ResolveCtx c) {
                c.types().isSameType(demand.parentType(), parent) && demand.segment() == segment ?
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

    private ExecutableElement methodNamed(final Class<?> type, final String name) {
        TypeUniverse.of(type).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
