package io.github.joke.percolate.docs.mapannotation;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

// tag::mapper[]
@Mapper
public interface AccountMapper {

    // `source` moves the value from a source path.
    @Map(target = "id", source = "form.id")
    // `constant` supplies a fixed literal with no source at all.
    @Map(target = "status", constant = "ACTIVE")
    // `defaultValue` falls back only when the source value is absent (a null reference or an empty Optional).
    @Map(target = "displayName", source = "form.displayName", defaultValue = "unknown")
    Account map(AccountForm form);
}
// end::mapper[]

// tag::model[]
final class AccountForm {
    private final String id;
    private final String displayName;

    AccountForm(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}

final class Account {
    private final String id;
    private final String status;
    private final String displayName;

    Account(String id, String status, String displayName) {
        this.id = id;
        this.status = status;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getDisplayName() {
        return displayName;
    }
}
// end::model[]
