package io.github.joke.percolate.docs.defaultmethod;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface ProductMapper {

    @Map(target = "price", source = "product.priceCents")
    ProductView map(Product product);

    // A `default` method supplies hand-written conversion logic. Percolate discovers it like any
    // single-argument method and calls it wherever it needs a String produced from a long,
    // emitting `this.formatPrice(product.getPriceCents())`.
    default String formatPrice(long cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
// end::mapper[]

// tag::model[]
final class Product {
    private final long priceCents;

    Product(long priceCents) {
        this.priceCents = priceCents;
    }

    public long getPriceCents() {
        return priceCents;
    }
}

final class ProductView {
    private final String price;

    ProductView(String price) {
        this.price = price;
    }

    public String getPrice() {
        return price;
    }
}
// end::model[]
