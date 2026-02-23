package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(ConversionProvider.class)
public final class SubtypeProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        List<? extends TypeMirror> supertypes = env.getTypeUtils().directSupertypes(source);
        return supertypes.stream()
                .filter(t -> t instanceof DeclaredType)
                .filter(t -> !t.toString().equals("java.lang.Object"))
                .map(t -> new Conversion(t, new ConversionEdge(ConversionEdge.Kind.SUBTYPE, source, t, "$expr")))
                .collect(toList());
    }
}
