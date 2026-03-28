package io.github.joke.percolate.processor.model;

import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public abstract class ReadAccessor {

    private final String name;
    private final TypeMirror type;
}
