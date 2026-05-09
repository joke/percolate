package io.github.joke.percolate.processor.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.spi.CallableMethods;
import io.github.joke.percolate.processor.spi.MethodCandidate;
import io.github.joke.percolate.processor.spi.ThisReceiver;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverCallableMethods implements Stage {

    private final Elements elements;
    private final Types types;

    @Override
    public void run(final MapperContext ctx) {
        final TypeElement mapperType = ctx.getMapperType();
        final CallableMethods callableMethods = discover(mapperType);
        ctx.setCallableMethods(callableMethods);
    }

    private CallableMethods discover(final TypeElement mapperType) {
        final List<ExecutableElement> allMembers = new ArrayList<>();
        for (final Element member : elements.getAllMembers(mapperType)) {
            if (member instanceof ExecutableElement) {
                allMembers.add((ExecutableElement) member);
            }
        }

        final Set<ExecutableElement> filtered = new HashSet<>();
        for (final Element member : allMembers) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            final ExecutableElement method = (ExecutableElement) member;
            if (isInObjectClass(method)) {
                continue;
            }
            if (method.getParameters().size() != 1) {
                continue;
            }
            filtered.add(method);
        }

        final Map<String, List<ExecutableElement>> indexByReturnType = new HashMap<>();
        for (final ExecutableElement method : filtered) {
            final TypeMirror returnType = method.getReturnType();
            final String key = returnType.toString();
            indexByReturnType.computeIfAbsent(key, k -> new ArrayList<>()).add(method);
        }

        return new IndexCallableMethods(indexByReturnType, types);
    }

    private boolean isInObjectClass(final ExecutableElement method) {
        final Element enclosing = method.getEnclosingElement();
        if (!(enclosing instanceof TypeElement)) {
            return false;
        }
        return "java.lang.Object"
                .equals(((TypeElement) enclosing).getQualifiedName().toString());
    }

    private static final class IndexCallableMethods implements CallableMethods {
        private final Map<String, List<ExecutableElement>> indexByReturnType;
        private final Types types;

        private IndexCallableMethods(
                final Map<String, List<ExecutableElement>> indexByReturnType, final Types types) {
            this.indexByReturnType = indexByReturnType;
            this.types = types;
        }

        @Override
        public Stream<MethodCandidate> producing(final TypeMirror outputType) {
            final List<ExecutableElement> candidates = new ArrayList<>();
            for (final List<ExecutableElement> methods : indexByReturnType.values()) {
                for (final ExecutableElement method : methods) {
                    final TypeMirror methodReturnType = method.getReturnType();
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
