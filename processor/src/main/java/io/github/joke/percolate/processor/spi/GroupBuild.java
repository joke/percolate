package io.github.joke.percolate.processor.spi;

import java.util.List;
import lombok.Value;

@Value
public final class GroupBuild {
    List<Slot> slots;
    GroupCodegen codegen;
}
