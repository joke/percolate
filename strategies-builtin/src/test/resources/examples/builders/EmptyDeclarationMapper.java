package examples.builders;

import io.github.joke.percolate.Mapper;

@Mapper
public interface EmptyDeclarationMapper {

    // No @Map at all. Percolate does not auto-map by name, so nothing is declared -- and neither
    // assembly form may vacuously satisfy the demand from the builder or the no-arg constructor.
    Gadget toGadget(GadgetDto dto);
}

final class Gadget {

    private final String label;

    Gadget(GadgetBuilder builder) {
        this.label = builder.label;
    }

    static GadgetBuilder builder() {
        return new GadgetBuilder();
    }

    public String getLabel() {
        return label;
    }
}

final class GadgetBuilder {

    String label = "";

    GadgetBuilder label(String value) {
        this.label = value;
        return this;
    }

    Gadget build() {
        return new Gadget(this);
    }
}

final class GadgetDto {

    private final String label;

    GadgetDto(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
