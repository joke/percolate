package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.model.ReadAccessor;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface SourcePropertyDiscovery {

    int priority();

    List<ReadAccessor> discover(TypeMirror type, Elements elements, Types types);
}
