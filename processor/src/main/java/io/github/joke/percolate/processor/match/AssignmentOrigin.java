package io.github.joke.percolate.processor.match;

/** Records how a {@link MappingAssignment} was produced by {@code MatchMappingsStage}. */
public enum AssignmentOrigin {

    /** The assignment came from an explicit {@code @Map} or {@code @MapList} directive. */
    EXPLICIT_MAP,

    /** The assignment was inferred because source and target share the same top-level property name. */
    AUTO_MAPPED,

    /** The assignment came from a {@code @Map(using = "...")} directive; the named method is the transform. */
    USING_ROUTED
}
