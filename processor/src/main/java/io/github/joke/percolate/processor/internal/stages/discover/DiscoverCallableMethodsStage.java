package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.MethodCandidate;
import io.github.joke.percolate.spi.ThisReceiver;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverCallableMethodsStage implements Stage {

    private static final int SINGLE_PARAM_COUNT = 1;

    private final Elements elements;
    private final Types types;
    private final NullabilityResolver resolver;

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
        return new IndexCallableMethods(indexByReturnType, types, new TypeSpaceAdapter(resolver));
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
        private final TypeSpaceAdapter adapter;

        private IndexCallableMethods(
                final Map<String, List<ExecutableElement>> indexByReturnType,
                final Types types,
                final TypeSpaceAdapter adapter) {
            this.indexByReturnType = indexByReturnType;
            this.types = types;
            this.adapter = adapter;
        }

        @Override
        public Stream<MethodCandidate> producing(final TypeMirror outputType) {
            return indexByReturnType.values().stream()
                    .flatMap(List::stream)
                    .filter(m -> types.isAssignable(m.getReturnType(), outputType))
                    .map(m -> new MethodCandidate(m, ThisReceiver.INSTANCE, methodSigOf(m)))
                    .collect(toUnmodifiableList())
                    .stream();
        }

        private io.github.joke.percolate.spi.types.MethodSig methodSigOf(final ExecutableElement method) {
            final var enclosing = method.getEnclosingElement();
            if (!(enclosing instanceof TypeElement)) {
                throw new IllegalStateException("a discovered method has no enclosing type: " + method);
            }
            return adapter.methodSigOf((TypeElement) enclosing, method, false);
        }
    }
}
