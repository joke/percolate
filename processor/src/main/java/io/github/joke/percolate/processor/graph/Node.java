package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.Nullability;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;

@Getter
public final class Node implements Comparable<Node> {
    private static final String UNKNOWN_TYPE = "?";

    private Optional<TypeMirror> type;
    private Optional<Nullability> nullability;
    private final Location loc;
    private final Scope scope;
    private final Optional<Node> parent;

    /**
     * The {@link ExpansionGroup}s this node belongs to, recorded as a lightweight insertion-ordered set of
     * {@link GroupId} labels. A node MAY belong to many groups at once (e.g. a boundary node that is one group's
     * {@code root} and another's input). Mutated only by the {@code Applier} (the single graph-mutation site) via
     * {@link #joinGroup}. Excluded from {@link #equals}/{@link #hashCode}, which stay instance-identity.
     */
    @Getter(lombok.AccessLevel.NONE)
    private final Set<GroupId> groups = new LinkedHashSet<>();

    /**
     * The {@code @Map} {@link Directive} in effect for the frontier this node was synthesized for. Populated by
     * the {@code Applier} when it synthesizes the input node of a {@code CONVERSION} step, so a downstream
     * strategy reads its per-binding configuration from local context (see design D5). Empty for nodes that did
     * not inherit a directive (boundary slots, seed roots/leaves).
     */
    private Optional<Directive> directive = Optional.empty();

    public Node(final Optional<TypeMirror> type, final Location loc, final Scope scope) {
        this(type, loc, scope, Optional.empty());
    }

    public Node(final Optional<TypeMirror> type, final Location loc, final Scope scope, final Optional<Node> parent) {
        this.type = type;
        this.nullability = type.isPresent() ? Optional.of(Nullability.UNKNOWN) : Optional.empty();
        this.loc = loc;
        this.scope = scope;
        this.parent = parent;
    }

    /**
     * Stamps the in-effect {@link Directive} onto this node. Called once, by the {@code Applier}, when the node
     * is synthesized as the input of a {@code CONVERSION} step (directive propagation, design D5).
     */
    public void inheritDirective(final Directive inherited) {
        this.directive = Optional.of(inherited);
    }

    /** The groups this node belongs to, in the order they were joined. Mutated only via {@link #joinGroup}. */
    public Set<GroupId> groups() {
        return Collections.unmodifiableSet(groups);
    }

    /** Tags this node into {@code id}'s group. Applier-only (the single graph-mutation site). */
    public void joinGroup(final GroupId id) {
        groups.add(id);
    }

    public void setTyping(final TypeMirror newType, final Nullability newNullability) {
        if (type.isPresent() || nullability.isPresent()) {
            throw new IllegalStateException(
                    "Node typing is already set; setTyping() requires both type and nullability to be empty");
        }
        this.type = Optional.of(newType);
        this.nullability = Optional.of(newNullability);
    }

    public String id() {
        final String prefix =
                (loc instanceof ElementLocation) ? parent.map(Node::id).orElseGet(scope::encode) : scope.encode();
        return prefix + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this);
    }

    private String typeEncode() {
        return type.map(TypeMirror::toString).orElse(UNKNOWN_TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(final Node other) {
        return id().compareTo(other.id());
    }
}
