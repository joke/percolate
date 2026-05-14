package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.Edge;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.Node;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

public final class SeedDsl {

    private final List<MethodBuilder> methods = new ArrayList<>();

    public static SeedDsl seed() {
        return new SeedDsl();
    }

    public MethodBuilder method(final String name) {
        final var builder = new MethodBuilder(name);
        methods.add(builder);
        return builder;
    }

    public MapperGraph build() {
        final var graph = new MapperGraph();
        for (final var method : methods) {
            method.buildInto(graph);
        }
        return graph;
    }

    public static final class MethodBuilder {

        private final String name;
        private final List<ArgSpec> args = new ArrayList<>();
        private @Nullable TypeMirror returnType;
        private final List<DirectiveSpec> directives = new ArrayList<>();

        MethodBuilder(final String name) {
            this.name = name;
        }

        public MethodBuilder arg(final String name, final TypeMirror type) {
            args.add(new ArgSpec(name, type));
            return this;
        }

        public MethodBuilder returns(final TypeMirror type) {
            this.returnType = type;
            return this;
        }

        public DirectiveSpec directive(final TargetLocation target, final SourceLocation source) {
            final var spec = new DirectiveSpec(target, source);
            directives.add(spec);
            return spec;
        }

        public SourceLocation source(final String path) {
            return new SourceLocation(AccessPath.of(path));
        }

        public TargetLocation target(final String path) {
            return new TargetLocation(TargetPath.of(path));
        }

        void buildInto(final MapperGraph graph) {
            final var paramTypes =
                    args.stream().map(a -> a.type.toString()).collect(java.util.stream.Collectors.toList());
            final var scopeName = name + "(" + String.join(",", paramTypes) + ")";
            final var scope = new HarnessScope(scopeName);

            final var sourceNodes = new java.util.LinkedHashMap<String, Node>();
            for (final var arg : args) {
                final var node = new Node(
                        Optional.of(arg.type), new SourceLocation(AccessPath.of(arg.name)), scope, Optional.empty());
                sourceNodes.put(arg.name, node);
                graph.addNode(node);
            }

            if (returnType != null) {
                final var targetNode = new Node(
                        Optional.of(returnType), new TargetLocation(TargetPath.of("")), scope, Optional.empty());
                graph.addNode(targetNode);
            }

            for (final var directive : directives) {
                final var sourceNode = sourceNodes.get(directive.sourceSegment);
                if (sourceNode != null) {
                    final var targetNode =
                            new Node(Optional.ofNullable(directive.tgtType), directive.target, scope, Optional.empty());
                    graph.addNode(targetNode);
                    final var edge = Edge.elementSeed(sourceNode, targetNode, "test.seed");
                    graph.addEdge(edge);
                } else {
                    final var sourceNode2 =
                            new Node(Optional.ofNullable(directive.srcType), directive.source, scope, Optional.empty());
                    final var targetNode =
                            new Node(Optional.ofNullable(directive.tgtType), directive.target, scope, Optional.empty());
                    graph.addNode(sourceNode2);
                    graph.addNode(targetNode);
                    final var edge = Edge.elementSeed(sourceNode2, targetNode, "test.seed");
                    graph.addEdge(edge);
                }
            }
        }
    }

    public static final class DirectiveSpec {

        final TargetLocation target;
        final SourceLocation source;
        final String sourceSegment;
        private @Nullable TypeMirror srcType;
        private @Nullable TypeMirror tgtType;

        DirectiveSpec(final TargetLocation target, final SourceLocation source) {
            this.target = target;
            this.source = source;
            final var segments = source.getPath().getSegments();
            this.sourceSegment = segments.isEmpty() ? "" : segments.get(segments.size() - 1);
        }

        public DirectiveSpec sourceType(final TypeMirror type) {
            this.srcType = type;
            return this;
        }

        public DirectiveSpec targetType(final TypeMirror type) {
            this.tgtType = type;
            return this;
        }
    }

    static final class ArgSpec {
        final String name;
        final TypeMirror type;

        ArgSpec(final String name, final TypeMirror type) {
            this.name = name;
            this.type = type;
        }
    }
}
