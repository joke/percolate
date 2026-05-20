package io.github.joke.percolate.processor.stages.seed

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.PathSegmentResolver
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.ResolvedSegment
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class SeedGraphSpec extends Specification {

    static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('$L', inputs.single()) }

    def 'typed source chain coexists with untyped chain when a resolver matches'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('mapHuman', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])
        def resolver = stubResolver(['lastName': new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP)])
        def seedGraph = new SeedGraph([resolver], stubCtx())

        when:
        def graph = seedGraph.apply(mappings)

        then: 'both untyped and typed nodes exist for person.lastName'
        def sourceNodes = graph.nodes()
                .filter { it.loc instanceof SourceLocation }
                .filter { it.loc.path.segments == ['person', 'lastName'] }
                .toList()
        sourceNodes.size() == 2
        sourceNodes.count { it.type.empty } == 1
        sourceNodes.count { it.type.present } == 1

        and: 'REALISED edge runs from typed src[person] to typed src[person.lastName]'
        def realised = graph.edges().filter { it.kind == EdgeKind.REALISED }.toList()
        realised.size() == 1
        realised[0].from.type.present
        realised[0].from.loc.path.segments == ['person']
        realised[0].to.type.present
        realised[0].to.loc.path.segments == ['person', 'lastName']

        and: 'MARKER edge runs from untyped seed leaf to typed leaf'
        def markers = graph.edges().filter { it.kind == EdgeKind.MARKER }.toList()
        markers.size() == 1
        markers[0].from.type.empty
        markers[0].to.type.present
        markers[0].to.loc.path.segments == ['person', 'lastName']

        and: 'one ExpansionGroup with the typed node as root and typed src[person] as slot'
        def groups = graph.groups().toList()
        groups.size() == 1
        groups[0].root.loc.path.segments == ['person', 'lastName']
        groups[0].slots.size() == 1
        groups[0].slots[0].loc.path.segments == ['person']
    }

    def 'resolution failure leaves the untyped chain unchanged and no typed leaf is created'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('mapHuman', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('x', 'person.unknown')
        def mappings = mappings(method, [directive])
        def resolver = stubResolver([:])
        def seedGraph = new SeedGraph([resolver], stubCtx())

        when:
        def graph = seedGraph.apply(mappings)

        then: 'no typed node is created for person.unknown'
        def typedSourceNodes = graph.nodes()
                .filter { it.loc instanceof SourceLocation }
                .filter { it.loc.path.segments == ['person', 'unknown'] }
                .filter { it.type.present }
                .toList()
        typedSourceNodes.empty

        and: 'no REALISED or MARKER edges, no groups'
        graph.edges().filter { it.kind == EdgeKind.REALISED }.toList().empty
        graph.edges().filter { it.kind == EdgeKind.MARKER }.toList().empty
        graph.groups().toList().empty

        and: 'untyped seed-leaf still present'
        graph.nodes()
                .filter { it.loc instanceof SourceLocation }
                .filter { it.loc.path.segments == ['person', 'unknown'] }
                .filter { it.type.empty }
                .toList()
                .size() == 1
    }

    def 'bridging seed edge originates from the typed source when full path resolves'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('mapHuman', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('lastName', 'person.lastName')
        def mappings = mappings(method, [directive])
        def resolver = stubResolver(['lastName': new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP)])
        def seedGraph = new SeedGraph([resolver], stubCtx())

        when:
        def graph = seedGraph.apply(mappings)

        then:
        def bridges = graph.edges()
                .filter { it.kind == EdgeKind.SEED && it.directive.present }
                .filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof io.github.joke.percolate.processor.graph.TargetLocation && it.to.loc.path.segments == ['lastName'] }
                .toList()
        bridges.size() == 1
        bridges[0].from.type.present
        bridges[0].from.loc.path.segments == ['person', 'lastName']
    }

    def 'bridging seed edge falls back to untyped seed leaf when path resolution fails mid-chain'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('mapHuman', [param('person', personType)], TypeUniverse.STRING)
        def directive = directive('x', 'person.address.street')
        def mappings = mappings(method, [directive])
        // resolver knows `address` but NOT `street`
        def resolver = stubResolver(['address': new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP)])
        def seedGraph = new SeedGraph([resolver], stubCtx())

        when:
        def graph = seedGraph.apply(mappings)

        then: 'bridge from untyped person.address.street'
        def bridges = graph.edges()
                .filter { it.kind == EdgeKind.SEED && it.directive.present }
                .filter { it.from.loc instanceof SourceLocation && it.to.loc instanceof io.github.joke.percolate.processor.graph.TargetLocation && it.to.loc.path.segments == ['x'] }
                .toList()
        bridges.size() == 1
        bridges[0].from.type.empty
        bridges[0].from.loc.path.segments == ['person', 'address', 'street']

        and: 'address still got typed (partial chain), but street did not'
        graph.nodes()
                .filter { it.loc instanceof SourceLocation && it.type.present && it.loc.path.segments == ['person', 'address'] }
                .toList()
                .size() == 1
        graph.nodes()
                .filter { it.loc instanceof SourceLocation && it.type.present && it.loc.path.segments == ['person', 'address', 'street'] }
                .toList()
                .empty
    }

    def 'two directives sharing a prefix reuse the typed prefix node'() {
        given:
        def personType = TypeUniverse.element('java.lang.Object').asType()
        def method = mockMethod('mapHuman', [param('person', personType)], TypeUniverse.STRING)
        def directives = [directive('a', 'person.address.street'), directive('b', 'person.address.city')]
        def mappings = mappings(method, directives)
        def resolver = stubResolver([
                'address': new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP),
                'street' : new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP),
                'city'   : new ResolvedSegment(TypeUniverse.STRING, NO_OP_CODEGEN, Weights.STEP),
        ])
        def seedGraph = new SeedGraph([resolver], stubCtx())

        when:
        def graph = seedGraph.apply(mappings)

        then: 'exactly one typed person.address node'
        graph.nodes()
                .filter { it.loc instanceof SourceLocation && it.type.present && it.loc.path.segments == ['person', 'address'] }
                .toList()
                .size() == 1

        and: 'three ExpansionGroups: address (once), street, city'
        def groups = graph.groups().toList()
        groups.size() == 3
        groups.count { it.root.loc.path.segments == ['person', 'address'] } == 1
        groups.count { it.root.loc.path.segments == ['person', 'address', 'street'] } == 1
        groups.count { it.root.loc.path.segments == ['person', 'address', 'city'] } == 1
    }

    private VariableElement param(final String name, final TypeMirror type) {
        def p = Mock(VariableElement)
        p.simpleName >> nameOf(name)
        p.asType() >> type
        p
    }

    private ExecutableElement mockMethod(
            final String name, final List<VariableElement> params, final TypeMirror returnType) {
        def m = Mock(ExecutableElement)
        m.simpleName >> nameOf(name)
        m.parameters >> params
        m.returnType >> returnType
        m
    }

    private Name nameOf(final String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }

    private MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, Mock(AnnotationMirror), null, null)
    }

    private MapperMappings mappings(final ExecutableElement method, final List<MappingDirective> directives) {
        new MapperMappings(null, [new MethodMappings(method, directives)])
    }

    private PathSegmentResolver stubResolver(final Map<String, ResolvedSegment> answers) {
        new PathSegmentResolver() {
            @Override
            Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
                Optional.ofNullable(answers[segment])
            }
        }
    }

    private ResolveCtx stubCtx() {
        new ResolveCtx() {
            @Override javax.lang.model.util.Types types() { TypeUniverse.types() }
            @Override javax.lang.model.util.Elements elements() { TypeUniverse.elements() }
            @Override javax.lang.model.element.TypeElement mapperType() { null }
            @Override ExecutableElement currentMethod() { null }
            @Override io.github.joke.percolate.spi.CallableMethods callableMethods() { null }
        }
    }
}
