package examples.setters;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

@Mapper
public interface SetterPreferenceMapper {

    // Ticket offers ALL THREE forms -- an all-args constructor, a fluent builder, and a no-arg
    // constructor with setters -- so all three gates match and the ranked preference decides.
    @Map(target = "code", source = "dto.code")
    Ticket toTicket(TicketDto dto);

    // Note offers only setters, so it assembles that way whatever the preference says --
    // the preference is a preference, never an exclusion.
    @Map(target = "code", source = "dto.code")
    Note toNote(TicketDto dto);
}

final class Ticket {

    private String code = "";

    Ticket() {
    }

    Ticket(String code) {
        this.code = code;
    }

    static TicketBuilder builder() {
        return new TicketBuilder();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

final class TicketBuilder {

    private String code = "";

    TicketBuilder code(String value) {
        this.code = value;
        return this;
    }

    Ticket build() {
        return new Ticket(code);
    }
}

final class Note {

    private String code = "";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

final class TicketDto {

    private final String code;

    TicketDto(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
