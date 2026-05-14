package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.graph.MapperGraph;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class MapperSpec {

    List<MethodSpec> methods;

    public MapperGraph toGraph() {
        final var dsl = SeedDsl.seed();
        for (final var method : methods) {
            final var mb = dsl.method(method.getName());
            for (final var arg : method.getArgs()) {
                mb.arg(arg.getName(), arg.getType());
            }
            mb.returns(method.getReturnType());
            for (final var directive : method.getDirectives()) {
                mb.directive(mb.target(directive.getTargetPath()), mb.source(directive.getSourcePath()));
            }
        }
        return dsl.build();
    }

    @Value
    public static class MethodSpec {
        String name;
        List<ArgSpec> args;
        TypeMirror returnType;
        List<DirectiveSpec> directives;
    }

    @Value
    public static class ArgSpec {
        String name;
        TypeMirror type;
    }

    @Value
    public static class DirectiveSpec {
        String targetPath;
        String sourcePath;
    }
}
