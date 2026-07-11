package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * What an {@link ExpansionStrategy} match offers: a single production, shaped as plain data. It carries a
 * human-readable, fully-typed {@code label} describing the production (e.g. {@code int→long},
 * {@code new Address(int, String)}, {@code getStreet()} — conversions use the glyph arrow {@code →}), the
 * operation's {@link Codegen} handle, its {@code weight}, its ordered {@link Port} signature (an AND over the
 * inputs it consumes), the produced output type and {@link Nullability}, optionally a {@link ChildScopeSpec}
 * (present only for a container element mapping — a scope-owning operation), and optionally a {@code callTarget} —
 * the {@link ExecutableElement} a method-call production invokes. The {@code label} is the operation's debug-graph
 * identity; it MUST NOT be derived from the codegen handle's runtime (lambda) class. The driver turns one spec into
 * one atomic {@code AddOperation} delta, fanning a demand out per port. A spec exposes no graph or engine surface;
 * strategies stay myopic.
 *
 * <p>The {@code callTarget} is a <b>neutral structural fact</b> — "this op calls this method" — recorded by a
 * method-call strategy from identity it already holds (its {@link MethodCandidate}); it is <b>not</b> a "self-call"
 * marker. A strategy cannot know the method currently being generated (the demand context exposes no current method),
 * so deciding whether a call is a degenerate self-call is the driver's concern, where the enclosing method scope is
 * known. Every non-method production carries no call target.
 *
 * <p>Construct via {@link #of} (a plain total operation), {@link #ofPartial} (a plain operation that may throw on
 * a structurally-valid input, e.g. {@code Optional.orElseThrow} — a {@code partial} producer the plan-extraction
 * totality rule deprioritises), {@link #callOf} (a total method-call production carrying its call target), or
 * {@link #mapping} (a scope-owning element mapping).
 *
 * <p>{@link #consumedOptionKeys} and {@link #memberRequests} are <b>additive, optional</b> neutral structural facts,
 * each defaulting to empty and set via {@link #withConsumedOptionKeys} / {@link #withMemberRequests} on the spec
 * returned by one of the factories above — existing factory call sites that never mention them are unaffected.
 * {@code consumedOptionKeys} is recorded by a strategy that read one or more {@code @Map} options (e.g.
 * {@code "format"}, {@code "zone"}) to produce this spec — the consumer declares consumption, mirroring
 * {@code callTarget}. The processor unions the stamped keys over a binding's <em>winning</em> plan to diagnose any
 * declared-but-unconsumed option (see the {@code directive-options} capability). {@code memberRequests} declares one
 * or more class-level members (see {@code code-generation}) the operation's codegen reaches by {@code dedupKey}
 * through {@link IncomingValues#member(String)} — the same indirection a hoisted local reaches its codegen through.
 * Strategies stay myopic: both are plain data, not graph access.
 */
@Value
public class OperationSpec {

    private static final Set<String> NO_OPTION_KEYS = Set.of();
    private static final List<MemberRequest> NO_MEMBER_REQUESTS = List.of();

    String label;
    Codegen codegen;
    int weight;
    List<Port> ports;
    TypeMirror outputType;
    Nullability outputNullness;
    Optional<ChildScopeSpec> childScope;
    boolean partial;
    Optional<ExecutableElement> callTarget;
    Set<String> consumedOptionKeys;
    List<MemberRequest> memberRequests;

    /** A plain total operation (constructor, accessor, conversion, constant, wrap, iterate, collect): no child scope. */
    public static OperationSpec of(
            final String label,
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                List.copyOf(ports),
                outputType,
                outputNullness,
                Optional.empty(),
                false,
                Optional.empty(),
                NO_OPTION_KEYS,
                NO_MEMBER_REQUESTS);
    }

    /** A plain partial operation (may throw on a structurally-valid input, e.g. {@code Optional.orElseThrow}). */
    public static OperationSpec ofPartial(
            final String label,
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                List.copyOf(ports),
                outputType,
                outputNullness,
                Optional.empty(),
                true,
                Optional.empty(),
                NO_OPTION_KEYS,
                NO_MEMBER_REQUESTS);
    }

    /**
     * A total method-call production ({@code receiver.method(arg)}) that records its {@code callTarget} — the neutral
     * fact "this op calls this method" — so the driver can apply its self-call rule without inspecting the label.
     */
    public static OperationSpec callOf(
            final String label,
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness,
            final ExecutableElement callTarget) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                List.copyOf(ports),
                outputType,
                outputNullness,
                Optional.empty(),
                false,
                Optional.of(callTarget),
                NO_OPTION_KEYS,
                NO_MEMBER_REQUESTS);
    }

    /** A scope-owning element mapping (stream map/flatMap, Optional.map): its child scope carries the transform. */
    public static OperationSpec mapping(
            final String label,
            final Codegen codegen,
            final int weight,
            final List<Port> ports,
            final TypeMirror outputType,
            final Nullability outputNullness,
            final ChildScopeSpec childScope) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                List.copyOf(ports),
                outputType,
                outputNullness,
                Optional.of(childScope),
                false,
                Optional.empty(),
                NO_OPTION_KEYS,
                NO_MEMBER_REQUESTS);
    }

    /** This spec, with its consumed-option-key set replaced by {@code consumedOptionKeys}. */
    public OperationSpec withConsumedOptionKeys(final Set<String> consumedOptionKeys) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                ports,
                outputType,
                outputNullness,
                childScope,
                partial,
                callTarget,
                Set.copyOf(consumedOptionKeys),
                memberRequests);
    }

    /** This spec, with its member-request list replaced by {@code memberRequests}. */
    public OperationSpec withMemberRequests(final List<MemberRequest> memberRequests) {
        return new OperationSpec(
                label,
                codegen,
                weight,
                ports,
                outputType,
                outputNullness,
                childScope,
                partial,
                callTarget,
                consumedOptionKeys,
                List.copyOf(memberRequests));
    }
}
