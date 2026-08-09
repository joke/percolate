package io.github.joke.percolate.violators;

/**
 * Violates: no method is static outside a genuine static context. Deliberately not a named constructor (it
 * returns {@code int}, not its own type) and deliberately not a stateless utility holder — the instance
 * method below keeps the class outside that shape, which is exactly what the rule's exemption turns on.
 */
public class HasStaticMethod {

    public static int helper() {
        return 1;
    }

    /** Present so the class is not an all-static holder, which the rule would exempt. */
    public int instanceMember() {
        return 2;
    }
}
