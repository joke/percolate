package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.FieldSpec;
import io.github.joke.percolate.spi.MemberRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

// The class-scoped sibling of HoistPlan (design D5 of change add-temporal-type-mapping): collects every
// strategy-requested MemberRequest reachable from any method's winning plan across the whole generated mapper
// type (not one method — a member may be shared across bodies), deduplicates them by
// MemberRequest.getDedupKey(), and names each distinct member via a class-scoped NameAllocator. A requesting
// operation's codegen reaches the allocated field's reference through
// io.github.joke.percolate.spi.IncomingValues.member(String) — the same indirection a hoisted local reaches its
// codegen through — so the composer stays field-syntax-free. It mutates neither the MapperGraph nor the
// ExtractedPlan.
@RequiredArgsConstructor
final class MemberPlan {

    private final Map<String, String> namesByDedupKey;
    private final Map<String, MemberRequest> requestByDedupKey;

    // The reference to the member registered under dedupKey.
    @VisibleForTesting
    CodeBlock reference(final String dedupKey) {
        final var name = namesByDedupKey.get(dedupKey);
        if (name == null) {
            throw new IllegalStateException("no member registered for dedup key: " + dedupKey);
        }
        return CodeBlock.of("$N", name);
    }

    // Every distinct requested member as a private static final field, in allocation order.
    @VisibleForTesting
    List<FieldSpec> fields() {
        return namesByDedupKey.entrySet().stream()
                .map(entry -> {
                    final var request = Objects.requireNonNull(requestByDedupKey.get(entry.getKey()));
                    return FieldSpec.builder(request.getFieldType(), entry.getValue(), PRIVATE, STATIC, FINAL)
                            .initializer(request.getInitializer())
                            .build();
                })
                .collect(toUnmodifiableList());
    }
}
