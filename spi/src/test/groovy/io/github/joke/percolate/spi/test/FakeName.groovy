package io.github.joke.percolate.spi.test

import javax.lang.model.element.Name

/** A javac-free {@link Name} stand-in wrapping a plain {@link String} — Spock's mock stubbing requires the exact
 *  declared return type, so a raw {@code String} can't stand in for {@code getSimpleName()}/{@code getQualifiedName()}
 *  directly. */
final class FakeName implements Name {

    private final String value

    FakeName(final String value) {
        this.value = value
    }

    @Override
    boolean contentEquals(final CharSequence other) {
        value.contentEquals(other)
    }

    @Override
    int length() {
        value.length()
    }

    @Override
    char charAt(final int index) {
        value.charAt(index)
    }

    @Override
    CharSequence subSequence(final int start, final int end) {
        value.subSequence(start, end)
    }

    @Override
    String toString() {
        value
    }
}
