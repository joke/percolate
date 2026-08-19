package io.github.joke.percolate.spi;

import io.github.joke.percolate.lib.javapoet.CodeBlock;

public interface IncomingValues {
    CodeBlock single();

    CodeBlock byGroupPosition(int idx);

    CodeBlock byName(String slotName);

    /**
     * The reference to a class-level member this operation requested via a {@link MemberRequest}, by its
     * {@code dedupKey} — the allocated name of the emitted field or method, whichever shape was requested.
     */
    CodeBlock member(String dedupKey);
}
