package io.github.joke.percolate.model;

public final class MapDirective {

    private final String target;
    private final String source;

    public MapDirective(String target, String source) {
        this.target = target;
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }
}
