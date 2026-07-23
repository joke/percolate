package io.github.joke.percolate.processor.internal.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.FieldSpec;
import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.lib.javapoet.TypeName;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.MemberRequest;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The class-scoped sibling of {@link HoistPlan} (design D5 of change {@code add-temporal-type-mapping}): collects
 * every strategy-requested {@link MemberRequest} reachable from any method's winning plan across the <b>whole</b>
 * generated mapper type (not one method — a member may be shared across bodies), deduplicates them by
 * {@link MemberRequest#getDedupKey()}, and names each distinct member via a class-scoped {@link NameAllocator}. A
 * requesting operation's codegen reaches the allocated field's reference through
 * {@link io.github.joke.percolate.spi.IncomingValues#member(String)} — the same indirection a hoisted local reaches
 * its codegen through — so the composer stays field-syntax-free. It mutates neither the {@link MapperGraph} nor the
 * {@link ExtractedPlan}.
 */
// IdentityHashMap for the reachability walk (mirrors HoistPlan.collectOps); LinkedHashMap for deterministic
// class-scope field-emission order — both single-threaded, no concurrent access.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class MemberPlan {

    private final Map<String, String> namesByDedupKey;
    private final Map<String, MemberRequest> requestByDedupKey;

    /** Builds the member plan for every {@link MemberRequest} reachable from any of {@code graph}'s return roots. */
    static MemberPlan forMapper(final MapperGraph graph, final ExtractedPlan plan) {
        final Set<Operation> ops = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Value> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        graph.returnRoots().forEach(root -> HoistPlan.collectOps(graph, plan, root, ops, seen));

        final var names = new NameAllocator();
        final Map<String, String> namesByDedupKey = new LinkedHashMap<>();
        final Map<String, MemberRequest> requestByDedupKey = new LinkedHashMap<>();
        ops.stream()
                .sorted(Comparator.comparing(Operation::id))
                .flatMap(op -> op.getMemberRequests().stream())
                .forEach(request -> {
                    requestByDedupKey.putIfAbsent(request.getDedupKey(), request);
                    namesByDedupKey.computeIfAbsent(
                            request.getDedupKey(), key -> names.newName(memberBase(request.getFieldType())));
                });
        return new MemberPlan(namesByDedupKey, requestByDedupKey);
    }

    /** The reference to the member registered under {@code dedupKey}. */
    CodeBlock reference(final String dedupKey) {
        final var name = namesByDedupKey.get(dedupKey);
        if (name == null) {
            throw new IllegalStateException("no member registered for dedup key: " + dedupKey);
        }
        return CodeBlock.of("$N", name);
    }

    /** Every distinct requested member as a {@code private static final} field, in allocation order. */
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

    /** A lower-camel base name derived from a class field type's simple name, or {@code "member"} when unknown. */
    static String memberBase(final TypeName fieldType) {
        if (!(fieldType instanceof ClassName)) {
            return "member";
        }
        final var simple = ((ClassName) fieldType).simpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
