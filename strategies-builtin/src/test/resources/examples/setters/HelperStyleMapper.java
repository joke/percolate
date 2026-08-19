package examples.setters;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

@Mapper
public interface HelperStyleMapper {

    // A plain bean, assembled through the generated helper whose modifiers
    // percolate.helpers.visibility and percolate.helpers.static control.
    @Map(target = "label", source = "dto.label")
    Badge toBadge(BadgeDto dto);
}

final class Badge {

    private String label = "";

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

final class BadgeDto {

    private final String label;

    BadgeDto(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
