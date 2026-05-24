package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.HarnessResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class PathSegmentGroupResolverSpec extends Specification {

    private static final GroupCodegen NOOP_GROUP_CODEGEN = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen GETTER_CODEGEN = { vars, inputs -> CodeBlock.of('$L.getValue()', inputs.single()) }
    private static final EdgeCodegen FIELD_CODEGEN = { vars, inputs -> CodeBlock.of('$L.value', inputs.single()) }

    def 'lowest-weight match wins when multiple resolvers match'() {
        given:
        def group = pathSegmentGroup()
        def cheap = new FakeGetterResolver(new ResolvedSegment(TypeUniverse.STRING, GETTER_CODEGEN, 1))
        def expensive = new FakeFieldResolver(new ResolvedSegment(TypeUniverse.STRING, FIELD_CODEGEN, 3))
        // Iteration order is class-name ascending: FakeFieldResolver(3) precedes FakeGetterResolver(1).
        // Best-by-weight must still pick the cheaper (FakeGetterResolver) match.
        def resolver = new PathSegmentGroupResolver([expensive, cheap])

        when:
        def match = resolver.resolveFor(group, HarnessResolveCtx.create())

        then:
        match.present
        match.get().segment.weight == 1
        match.get().resolverClassName == FakeGetterResolver.name
    }

    def 'first-by-iteration wins on tied weight'() {
        given:
        def group = pathSegmentGroup()
        def first = new FakeGetterResolver(new ResolvedSegment(TypeUniverse.STRING, GETTER_CODEGEN, 2))
        def second = new FakeFieldResolver(new ResolvedSegment(TypeUniverse.STRING, FIELD_CODEGEN, 2))
        def resolver = new PathSegmentGroupResolver([first, second])

        when:
        def match = resolver.resolveFor(group, HarnessResolveCtx.create())

        then:
        match.present
        match.get().resolverClassName == FakeGetterResolver.name
    }

    def 'returns empty when no resolver matches'() {
        given:
        def group = pathSegmentGroup()
        def silent = new SilentResolver()
        def resolver = new PathSegmentGroupResolver([silent])

        when:
        def match = resolver.resolveFor(group, HarnessResolveCtx.create())

        then:
        !match.present
    }

    private static ExpansionGroup pathSegmentGroup() {
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def slot = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('bean')), scope)
        def root = new Node(Optional.empty(), new SourceLocation(AccessPath.of('bean').append('value')), scope)
        graph.addNode(slot)
        graph.addNode(root)
        def group = ExpansionGroup.of(root, [slot], NOOP_GROUP_CODEGEN, 'test.PathSegmentGroup', Set.of(), graph)
        graph.addGroup(group)
        group
    }

    private static final class FakeGetterResolver implements PathSegmentResolver {
        private final ResolvedSegment result
        FakeGetterResolver(final ResolvedSegment result) { this.result = result }
        @Override
        Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
            Optional.of(result)
        }
    }

    private static final class FakeFieldResolver implements PathSegmentResolver {
        private final ResolvedSegment result
        FakeFieldResolver(final ResolvedSegment result) { this.result = result }
        @Override
        Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
            Optional.of(result)
        }
    }

    private static final class SilentResolver implements PathSegmentResolver {
        @Override
        Optional<ResolvedSegment> resolve(final TypeMirror parentType, final String segment, final ResolveCtx ctx) {
            Optional.empty()
        }
    }
}
