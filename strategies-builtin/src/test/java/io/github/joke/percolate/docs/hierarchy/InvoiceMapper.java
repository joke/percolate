package io.github.joke.percolate.docs.hierarchy;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::base[]
interface BillingConversions {

    @Map(target = "sku", source = "item.sku")
    LineItemView mapLineItem(LineItem item);

    // A `default` method is skipped for generation but still callable as a conversion — even though it is
    // declared on a super-interface, not the `@Mapper` type itself.
    default String reference(long id) {
        return "INV-" + id;
    }
}
// end::base[]

// tag::mapper[]
@Mapper
public abstract class InvoiceMapper implements BillingConversions {

    @Map(target = "total", source = "invoice.totalCents")
    public abstract InvoiceView map(Invoice invoice);

    // A concrete method on the abstract class itself is skipped for generation, exactly like a `default`
    // method on an interface, and remains callable as a conversion.
    long normalizeId(String rawId) {
        return Long.parseLong(rawId);
    }
}
// end::mapper[]

// tag::model[]
final class LineItem {
    private final String sku;

    LineItem(String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}

final class LineItemView {
    private final String sku;

    LineItemView(String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}

final class Invoice {
    private final long totalCents;

    Invoice(long totalCents) {
        this.totalCents = totalCents;
    }

    public long getTotalCents() {
        return totalCents;
    }
}

final class InvoiceView {
    private final long total;

    InvoiceView(long total) {
        this.total = total;
    }

    public long getTotal() {
        return total;
    }
}
// end::model[]
