package io.github.joke.percolate.processor.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.spi.CallableMethods;
import io.github.joke.percolate.processor.spi.MethodCandidate;
import io.github.joke.percolate.processor.spi.ThisReceiver;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

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
        final List<ExecutableElement> allMembers = new ArrayList<>();
        for (final var member : elements.getAllMembers(mapperType)) {
            if (member instanceof ExecutableElement) {
                allMembers.add((ExecutableElement) member);
            }
        }

        final Set<ExecutableElement> filtered = new HashSet<>();
        for (final Element member : allMembers) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            final var method = (ExecutableElement) member;
            if (isInObjectClass(method)) {
                continue;
            }
            if (method.getParameters().size() != SINGLE_PARAM_COUNT) {
                continue;
            }
            filtered.add(method);
        }

        final Map<String, List<ExecutableElement>> indexByReturnType = new ConcurrentHashMap<>();
        for (final var method : filtered) {
            final var returnType = method.getReturnType();
            final var key = returnType.toString();
            indexByReturnType.computeIfAbsent(key, k -> new ArrayList<>()).add(method);
        }

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
            final List<ExecutableElement> candidates = new ArrayList<>();
            for (final var methods : indexByReturnType.values()) {
                for (final var method : methods) {
                    final var methodReturnType = method.getReturnType();
                    if (types.isAssignable(methodReturnType, outputType)) {
                        candidates.add(method);
                    }
                }
            }
            return candidates.stream()
                    .map(m -> new MethodCandidate(m, ThisReceiver.INSTANCE))
                    .collect(toUnmodifiableList())
                    .stream();
        }
    }
}
