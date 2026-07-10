package io.github.joke.percolate.docs.collections;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

// tag::mapper[]
@Mapper
public interface TeamMapper {

    // Same-kind, element-converting: List<Member> -> List<MemberView>, reusing `toView` per element.
    @Map(target = "members", source = "team.members")
    TeamView map(Team team);

    @Map(target = "name", source = "member.name")
    MemberView toView(Member member);

    // Cross-kind: Set<String> -> List<String>. No @Map — a top-level method whose return type is
    // itself a container needs no directive; the sole parameter is the source.
    List<String> toSortedTags(Set<String> tags);

    // A Stream source composes the same way as any other container.
    Set<String> toUniqueTags(Stream<String> tags);

    // Presence composed inside a container: Optional elements are dropped while collecting
    // (a flat-map over each element's 0-or-1 stream), so only the present tags survive.
    Set<String> toPresentTags(List<Optional<String>> maybeTags);

    // Composed containers: a List of Optional elements converts into an Optional of a Set. The pipeline
    // chains iterate(List) -> flatMap(Optional's 0-or-1 stream) -> map(element conversion) -> collect(Set)
    // -> wrap(Optional) -- no dedicated "nested container" composer is needed, the same building blocks
    // just chain one more time.
    Optional<Set<MemberView>> toRoster(List<Optional<Member>> maybeMembers);
}
// end::mapper[]

// tag::model[]
final class Team {
    private final List<Member> members;

    Team(List<Member> members) {
        this.members = members;
    }

    public List<Member> getMembers() {
        return members;
    }
}

final class Member {
    private final String name;

    Member(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

final class TeamView {
    private final List<MemberView> members;

    TeamView(List<MemberView> members) {
        this.members = members;
    }

    public List<MemberView> getMembers() {
        return members;
    }
}

final class MemberView {
    private final String name;

    MemberView(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
// end::model[]
