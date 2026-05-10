package io.github.joke.percolate.processor.spi;

import lombok.Value;

import java.util.List;

@Value
public final class GroupBuild {
    List<Slot> slots;
    GroupCodegen codegen;
}
