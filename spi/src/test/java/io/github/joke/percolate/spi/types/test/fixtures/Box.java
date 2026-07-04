package io.github.joke.percolate.spi.types.test.fixtures;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName") // fixture for a record-style value() accessor
public class Box<T> {

    private final T value;

    public Box(final T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }
}
