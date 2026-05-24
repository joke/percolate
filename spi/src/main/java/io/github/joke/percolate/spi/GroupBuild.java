package io.github.joke.percolate.spi;

import java.util.List;
import lombok.Value;

@Value
public class GroupBuild {
    List<Slot> slots;
    GroupCodegen codegen;
}
