package io.github.joke.percolate.spi.builtins.fixtures;

public class PersonWithAddress {
    private Address address;

    public Address getAddress() {
        return address;
    }

    public void setAddress(final Address address) {
        this.address = address;
    }

    public static class Address {
        private String street;

        public String getStreet() {
            return street;
        }

        public void setStreet(final String street) {
            this.street = street;
        }
    }
}
