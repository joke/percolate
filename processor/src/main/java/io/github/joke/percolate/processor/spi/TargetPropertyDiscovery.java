package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.model.WriteAccessor;
import java.util.List;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public interface TargetPropertyDiscovery {

    int priority();

    List<WriteAccessor> discover(TypeMirror type, Elements elements, Types types);
}
