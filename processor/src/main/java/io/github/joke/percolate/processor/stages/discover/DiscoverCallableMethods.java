package io.github.joke.percolate.processor.stages.discover;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.stages.Stage;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.ThisReceiver;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverCallableMethods implements Stage {

    private static final int SINGLE_PARAM_COUNT = 1;

    private final Elements elements;
    private final Types types;

    @Override
    public void run(final MapperContext ctx) {
        final var mapperType = ctx.getMapperType();
        final var callableMethods = discover(mapperType);
        ctx.setCallableMethods(callableMethods);
    }

    private CallableMethods discover(final TypeElement mapperType) {
        final Map<String, List<ExecutableElement>> indexByReturnType = elements.getAllMembers(mapperType).stream()
                .filter(ExecutableElement.class::isInstance)
                .map(ExecutableElement.class::cast)
                .filter(m -> m.getKind() == ElementKind.METHOD)
                .filter(m -> !isInObjectClass(m))
                .filter(m -> m.getParameters().size() == SINGLE_PARAM_COUNT)
                .collect(toCollection(HashSet::new))
                .stream()
                .collect(groupingBy(m -> m.getReturnType().toString(), ConcurrentHashMap::new, toList()));
        return new IndexCallableMethods(indexByReturnType, types);
    }

    private boolean isInObjectClass(final ExecutableElement method) {
        final var enclosing = method.getEnclosingElement();
        return enclosing instanceof TypeElement
                && "java.lang.Object"
                        .equals(((TypeElement) enclosing).getQualifiedName().toString());
    }

    private static final class IndexCallableMethods implements CallableMethods {
        private final Map<String, List<ExecutableElement>> indexByReturnType;
        private final Types types;

        private IndexCallableMethods(final Map<String, List<ExecutableElement>> indexByReturnType, final Types types) {
            this.indexByReturnType = indexByReturnType;
            this.types = types;
        }

        @Override
        public Stream<MethodCandidate> producing(final TypeMirror outputType) {
            return indexByReturnType.values().stream()
                    .flatMap(List::stream)
                    .filter(m -> types.isAssignable(m.getReturnType(), outputType))
                    .map(m -> new MethodCandidate(m, ThisReceiver.INSTANCE))
                    .collect(toUnmodifiableList())
                    .stream();
        }
    }
}
