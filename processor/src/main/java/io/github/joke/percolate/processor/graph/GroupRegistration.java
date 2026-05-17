package io.github.joke.percolate.processor.graph;

import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class GroupRegistration {
    final String groupId;
    final io.github.joke.percolate.spi.GroupCodegen codegen;

    public GroupRegistration(final String groupId, final io.github.joke.percolate.spi.GroupCodegen codegen) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.codegen = Objects.requireNonNull(codegen, "codegen must not be null");
    }
}
