package io.github.joke.percolate.spi.builtins.fixtures;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public class AddressFluent {

    private String street;

    public String street() {
        return street;
    }

    public void street(final String street) {
        this.street = street;
    }
}
