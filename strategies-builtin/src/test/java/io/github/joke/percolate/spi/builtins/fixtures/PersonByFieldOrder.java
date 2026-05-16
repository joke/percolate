package io.github.joke.percolate.spi.builtins.fixtures;

public class PersonByFieldOrder {
    private final int age;
    private final String name;

    public PersonByFieldOrder(final int age, final String name) {
        this.age = age;
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public String getName() {
        return name;
    }
}
