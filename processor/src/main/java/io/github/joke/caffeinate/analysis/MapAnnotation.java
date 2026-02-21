package io.github.joke.caffeinate.analysis;

public final class MapAnnotation {
    private final String target;
    private final String source;

    public MapAnnotation(String target, String source) {
        this.target = target;
        this.source = source;
    }

    public String getTarget() { return target; }
    public String getSource() { return source; }
}
