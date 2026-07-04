package io.github.joke.percolate.processor.discover;

/**
 * A self-referencing fixture for {@code TypeSpaceAdapterSpec}: exercises the discovery adapter's cycle-safe
 * closure walk (design D6 — "a visited set over qualified names ... Person → Person") and its array-typed
 * member handling.
 */
public class PersonNode {

    private final String name;
    private final PersonNode[] children;

    public PersonNode(final String name, final PersonNode... children) {
        this.name = name;
        this.children = children.clone();
    }

    public String getName() {
        return name;
    }

    public PersonNode[] getChildren() {
        return children.clone();
    }
}
