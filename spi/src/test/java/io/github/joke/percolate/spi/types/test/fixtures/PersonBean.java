package io.github.joke.percolate.spi.types.test.fixtures;

public class PersonBean {

    private final String name;
    private final int age;

    public PersonBean(final String name, final int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
