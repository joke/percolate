package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.DirectiveInput;
import java.util.List;
import lombok.RequiredArgsConstructor;

// The generic Directive assembled at one target path from whatever a
// io.github.joke.percolate.spi.DirectiveReader declared there — a bind's source path (empty when the path was
// never bound, e.g. a @MapEnum-only root) plus every input attached at that same path (design D7 of change
// decouple-engine-from-strategy-semantics). The core never inspects a key.
@RequiredArgsConstructor
// each field backs the Directive accessor of the same name
@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
class GenericDirective implements Directive {

    private final List<String> sourcePath;
    private final List<DirectiveInput> inputs;

    @Override
    public List<String> sourcePath() {
        return sourcePath;
    }

    @Override
    public List<DirectiveInput> inputs() {
        return inputs;
    }
}
