package io.github.joke.percolate.spi.builtins.fixtures;

@SuppressWarnings("PMD.DataClass")
public class PersonBean {
    private String name;
    private int age;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(final int age) {
        this.age = age;
    }
}
