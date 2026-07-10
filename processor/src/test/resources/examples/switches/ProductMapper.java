package examples.switches;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface ProductMapper {

    @Map(target = "name", source = "product.name")
    @Map(target = "price", source = "product.price")
    ProductView map(Product product);
}
// end::mapper[]

// tag::model[]
final class Product {
    private final String name;
    private final int price;

    Product(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }
}

final class ProductView {
    private final String name;
    private final int price;

    ProductView(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }
}
// end::model[]
