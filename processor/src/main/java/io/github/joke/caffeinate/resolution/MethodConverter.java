package io.github.joke.caffeinate.resolution;

import javax.lang.model.element.ExecutableElement;

/** A converter backed by an explicit mapper method (abstract or default). */
public final class MethodConverter implements Converter {
    private final ExecutableElement method;

    public MethodConverter(ExecutableElement method) {
        this.method = method;
    }

    public ExecutableElement getMethod() {
        return method;
    }
}
