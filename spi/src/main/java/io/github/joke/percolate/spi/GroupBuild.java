package io.github.joke.percolate.spi;

import lombok.Value;

import java.util.List;

@Value
public class GroupBuild {
    List<Slot> slots;
    GroupCodegen codegen;
}
