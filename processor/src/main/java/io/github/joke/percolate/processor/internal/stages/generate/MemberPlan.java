package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.FieldSpec;
import io.github.joke.percolate.lib.javapoet.MethodSpec;
import io.github.joke.percolate.lib.javapoet.ParameterSpec;
import io.github.joke.percolate.processor.HelperStyle;
import io.github.joke.percolate.spi.MemberRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.lib.javapoet.MethodSpec.methodBuilder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;

// The class-scoped member emission plan: which strategy-requested member each dedup key resolves to, the name it
// was allocated, and the modifiers the percolate.helpers.* switches put on it (change add-setter-assembly). Both
// request kinds share one dedup namespace and one allocator, so a field and a method never collide.
@RequiredArgsConstructor
final class MemberPlan {

    private final Map<String, String> namesByDedupKey;
    private final Map<String, MemberRequest> requestByDedupKey;
    private final HelperStyle style;

    @VisibleForTesting
    CodeBlock reference(final String dedupKey) {
        final var name = namesByDedupKey.get(dedupKey);
        if (name == null) {
            throw new IllegalStateException("no member registered for dedup key: " + dedupKey);
        }
        return CodeBlock.of("$N", name);
    }

    // Every field request, in allocation order.
    @VisibleForTesting
    List<FieldSpec> fields() {
        return namesByDedupKey.entrySet().stream()
                .filter(entry -> requestFor(entry) instanceof MemberRequest.Field)
                .map(entry -> fieldFor(entry.getValue(), (MemberRequest.Field) requestFor(entry)))
                .collect(toUnmodifiableList());
    }

    // Every method request, in allocation order.
    @VisibleForTesting
    List<MethodSpec> methods() {
        return namesByDedupKey.entrySet().stream()
                .filter(entry -> requestFor(entry) instanceof MemberRequest.Method)
                .map(entry -> methodFor(entry.getValue(), (MemberRequest.Method) requestFor(entry)))
                .collect(toUnmodifiableList());
    }

    @VisibleForTesting
    MemberRequest requestFor(final Map.Entry<String, String> entry) {
        return requireNonNull(requestByDedupKey.get(entry.getKey()));
    }

    @VisibleForTesting
    FieldSpec fieldFor(final String name, final MemberRequest.Field request) {
        return FieldSpec.builder(request.getFieldType(), name)
                .addModifiers(style.fieldModifiers())
                .initializer(request.getInitializer())
                .build();
    }

    @VisibleForTesting
    MethodSpec methodFor(final String name, final MemberRequest.Method request) {
        final var builder = methodBuilder(name)
                .addModifiers(style.memberModifiers())
                .returns(request.getReturnType())
                .addCode(request.getBody());
        request.getParameters().forEach(parameter -> builder.addParameter(parameterSpec(parameter)));
        return builder.build();
    }

    @VisibleForTesting
    ParameterSpec parameterSpec(final MemberRequest.Parameter parameter) {
        return ParameterSpec.builder(parameter.getType(), parameter.getName()).build();
    }
}
