package io.github.joke.percolate.docs.mapannotation;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import java.util.Optional;

// tag::mapper[]
@Mapper
public interface AccountMapper {

    // `source` moves the value from a source path.
    @Map(target = "id", source = "form.id")
    // `constant` supplies a fixed literal with no source at all.
    @Map(target = "status", constant = "ACTIVE")
    // `defaultValue` falls back only when the source value is absent (a null reference or an empty Optional).
    @Map(target = "displayName", source = "form.displayName", defaultValue = "unknown")
    // An Optional source falls back with `.orElse(...)` instead of a null-guard.
    @Map(target = "nickname", source = "form.nickname", defaultValue = "anon")
    // A widening primitive conversion (int -> long) needs no conversion method — it is a plain source read.
    @Map(target = "balanceCents", source = "form.balanceCents")
    Account map(AccountForm form);
}
// end::mapper[]

// tag::model[]
final class AccountForm {
    private final String id;
    private final String displayName;
    private final Optional<String> nickname;
    private final int balanceCents;

    AccountForm(String id, String displayName, Optional<String> nickname, int balanceCents) {
        this.id = id;
        this.displayName = displayName;
        this.nickname = nickname;
        this.balanceCents = balanceCents;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Optional<String> getNickname() {
        return nickname;
    }

    public int getBalanceCents() {
        return balanceCents;
    }
}

final class Account {
    private final String id;
    private final String status;
    private final String displayName;
    private final String nickname;
    private final long balanceCents;

    Account(String id, String status, String displayName, String nickname, long balanceCents) {
        this.id = id;
        this.status = status;
        this.displayName = displayName;
        this.nickname = nickname;
        this.balanceCents = balanceCents;
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

    public String getNickname() {
        return nickname;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}
// end::model[]
