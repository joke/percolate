package io.github.joke.percolate.docs.ambient;

import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface AmbientMapper {

    @Map(target = "address", source = "customer.address")
    @Map(target = "orderRef", source = "order.id")
    OrderView map(Customer customer, @Ambient Order order);

    // A default conversion method with a second, @Ambient argument — the case @Ambient unlocks: a
    // conversion method taking more than one argument, with no assembly strategy involved.
    default AddressView mapAddress(CustomerAddress address, @Ambient Order order) {
        return new AddressView(address.getStreet(), order.getId());
    }
}
// end::mapper[]

// tag::model[]
final class Customer {
    private final CustomerAddress address;

    Customer(CustomerAddress address) {
        this.address = address;
    }

    public CustomerAddress getAddress() {
        return address;
    }
}

final class CustomerAddress {
    private final String street;

    CustomerAddress(String street) {
        this.street = street;
    }

    public String getStreet() {
        return street;
    }
}

final class Order {
    private final String id;

    Order(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

final class OrderView {
    private final AddressView address;
    private final String orderRef;

    OrderView(AddressView address, String orderRef) {
        this.address = address;
        this.orderRef = orderRef;
    }

    public AddressView getAddress() {
        return address;
    }

    public String getOrderRef() {
        return orderRef;
    }
}

final class AddressView {
    private final String street;
    private final String orderRef;

    AddressView(String street, String orderRef) {
        this.street = street;
        this.orderRef = orderRef;
    }

    public String getStreet() {
        return street;
    }

    public String getOrderRef() {
        return orderRef;
    }
}
// end::model[]
