package io.github.joke.percolate.docs.ambient;

import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import java.util.List;

// tag::lambda-mapper[]
@Mapper
public interface AmbientLambdaMapper {

    @Map(target = "lines", source = "order.items")
    Receipt map(CustomerOrder order, @Ambient Store store);

    // Reached once per element inside the generated `.stream().map(...)` — the ambient environment is
    // inherited into the element (child) scope for free, via the lambda's captured `store` variable.
    default LineView mapLine(Item item, @Ambient Store store) {
        return new LineView(item.getSku(), store.getName());
    }
}
// end::lambda-mapper[]

// tag::lambda-model[]
final class CustomerOrder {
    private final List<Item> items;

    CustomerOrder(List<Item> items) {
        this.items = items;
    }

    public List<Item> getItems() {
        return items;
    }
}

final class Item {
    private final String sku;

    Item(String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}

final class Store {
    private final String name;

    Store(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

final class Receipt {
    private final List<LineView> lines;

    Receipt(List<LineView> lines) {
        this.lines = lines;
    }

    public List<LineView> getLines() {
        return lines;
    }
}

final class LineView {
    private final String sku;
    private final String storeName;

    LineView(String sku, String storeName) {
        this.sku = sku;
        this.storeName = storeName;
    }

    public String getSku() {
        return sku;
    }

    public String getStoreName() {
        return storeName;
    }
}
// end::lambda-model[]
