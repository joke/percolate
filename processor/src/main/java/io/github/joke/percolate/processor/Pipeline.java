package io.github.joke.percolate.processor;

import com.palantir.javapoet.JavaFile;
import jakarta.inject.Inject;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

final class Pipeline {

    @Inject
    Pipeline() {}

    @Nullable
    JavaFile process(final TypeElement element) {
        return null;
    }
}
