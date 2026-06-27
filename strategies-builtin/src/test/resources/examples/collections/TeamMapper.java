package io.github.joke.percolate.docs.collections;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import java.util.List;

// tag::mapper[]
@Mapper
public interface TeamMapper {

    // The `members` field is a List. Percolate maps it element-by-element, reusing the
    // `toView` mapper method for each element.
    @Map(target = "members", source = "team.members")
    TeamView map(Team team);

    @Map(target = "name", source = "member.name")
    MemberView toView(Member member);
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
