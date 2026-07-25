package io.github.joke.percolate.docs.multiparameter;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::twoparam-mapper[]
@Mapper
public interface TwoParamMapper {

    @Map(target = "customerName", source = "customer.name")
    @Map(target = "street", source = "address.street")
    OrderView map(Customer customer, Address address);
}
// end::twoparam-mapper[]

// tag::twoparam-model[]
final class Customer {
    private final String name;

    Customer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

final class Address {
    private final String street;

    Address(String street) {
        this.street = street;
    }

    public String getStreet() {
        return street;
    }
}

final class OrderView {
    private final String customerName;
    private final String street;

    OrderView(String customerName, String street) {
        this.customerName = customerName;
        this.street = street;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getStreet() {
        return street;
    }
}
// end::twoparam-model[]
