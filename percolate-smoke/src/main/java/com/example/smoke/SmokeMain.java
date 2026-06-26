package com.example.smoke;

/**
 * Runs the generated {@code PersonMapperImpl} and checks the result. Referencing the generated type and running
 * it on a runtime classpath that carries no percolate artifact is the black-box assertion: the published path
 * produced a working, zero-footprint mapper.
 */
public final class SmokeMain {

    private SmokeMain() {}

    public static void main(final String[] args) {
        final Human human = new PersonMapperImpl().map(new Person("Alice", 30));
        if (!"Alice".equals(human.getFirstName()) || human.getAge() != 30) {
            throw new AssertionError("smoke mapping failed: " + human.getFirstName() + " / " + human.getAge());
        }
    }
}
