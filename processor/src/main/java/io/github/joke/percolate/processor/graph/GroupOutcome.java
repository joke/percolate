package io.github.joke.percolate.processor.graph;

import lombok.Value;

import java.util.Optional;

@Value
public class GroupOutcome {

    public enum Kind {
        SAT,
        UNSAT_NO_PLAN,
        UNSAT_DID_NOT_CONVERGE
    }

    ExpansionGroup group;
    Kind kind;
    Optional<Node> failingSlot;

    public static GroupOutcome sat(final ExpansionGroup group) {
        return new GroupOutcome(group, Kind.SAT, Optional.empty());
    }

    public static GroupOutcome unsatNoPlan(final ExpansionGroup group, final Node failingSlot) {
        return new GroupOutcome(group, Kind.UNSAT_NO_PLAN, Optional.of(failingSlot));
    }

    public static GroupOutcome unsatDidNotConverge(final ExpansionGroup group, final Node failingSlot) {
        return new GroupOutcome(group, Kind.UNSAT_DID_NOT_CONVERGE, Optional.of(failingSlot));
    }
}
