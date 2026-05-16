package io.github.joke.percolate.spi.builtins.fixtures;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public final class PersonRecord {
    private final int age;
    private final String name;

    public PersonRecord(final int age, final String name) {
        this.age = age;
        this.name = name;
    }

    public int age() {
        return age;
    }

    public String name() {
        return name;
    }
}
