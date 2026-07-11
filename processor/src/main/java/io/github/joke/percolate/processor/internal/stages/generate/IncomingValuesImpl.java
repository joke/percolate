package io.github.joke.percolate.processor.internal.stages.generate;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.IncomingValues;
import java.util.List;
import java.util.Map;

final class IncomingValuesImpl implements IncomingValues {

    private static final int ONE = 1;

    private final List<CodeBlock> positional;
    private final Map<String, CodeBlock> nameMap;
    private final Map<String, CodeBlock> memberMap;

    IncomingValuesImpl(final List<CodeBlock> positional, final Map<String, CodeBlock> nameMap) {
        this(positional, nameMap, Map.of());
    }

    IncomingValuesImpl(
            final List<CodeBlock> positional,
            final Map<String, CodeBlock> nameMap,
            final Map<String, CodeBlock> memberMap) {
        this.positional = List.copyOf(positional);
        this.nameMap = Map.copyOf(nameMap);
        this.memberMap = Map.copyOf(memberMap);
    }

    @Override
    public CodeBlock single() {
        if (positional.isEmpty()) {
            throw new IllegalStateException("single() called with no positional entries");
        }
        if (positional.size() > ONE) {
            throw new IllegalStateException("single() called with " + positional.size()
                    + " positional entries; use byGroupPosition or byName instead");
        }
        return positional.get(0);
    }

    @Override
    public CodeBlock byGroupPosition(final int idx) {
        return positional.get(idx);
    }

    @Override
    public CodeBlock byName(final String slotName) {
        final var result = nameMap.get(slotName);
        if (result == null) {
            throw new IllegalStateException("No incoming value for slot: " + slotName);
        }
        return result;
    }

    @Override
    public CodeBlock member(final String dedupKey) {
        final var result = memberMap.get(dedupKey);
        if (result == null) {
            throw new IllegalStateException("No member reference for dedup key: " + dedupKey);
        }
        return result;
    }

    static IncomingValues of(final CodeBlock singleValue) {
        return new IncomingValuesImpl(List.of(singleValue), Map.of());
    }
}
