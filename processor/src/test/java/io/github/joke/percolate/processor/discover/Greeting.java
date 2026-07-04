package io.github.joke.percolate.processor.discover;

/**
 * A fixture for {@code TypeSpaceAdapterSpec}: a non-JDK interface with a static factory, a default method, and an
 * abstract method — exercises the discovery adapter's {@code MemberFlag} derivation for all three.
 */
public interface Greeting {

    static Greeting of(final String text) {
        return () -> text;
    }

    default String shout() {
        return text().toUpperCase(java.util.Locale.ROOT);
    }

    String text();
}
