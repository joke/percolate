package io.github.joke.percolate.processor;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class StageResult<T> {

    private final @Nullable T value;
    private final List<Diagnostic> errors;

    private StageResult(final @Nullable T value, final List<Diagnostic> errors) {
        this.value = value;
        this.errors = errors;
    }

    public static <T> StageResult<T> success(final T value) {
        return new StageResult<>(value, Collections.emptyList());
    }

    public static <T> StageResult<T> failure(final List<Diagnostic> errors) {
        return new StageResult<>(null, errors);
    }

    public boolean isSuccess() {
        return value != null && errors.isEmpty();
    }

    @SuppressWarnings("NullAway")
    public T value() {
        if (!isSuccess()) {
            throw new IllegalStateException("Cannot access value of a failed StageResult");
        }
        return value;
    }

    public List<Diagnostic> errors() {
        return errors;
    }
}
