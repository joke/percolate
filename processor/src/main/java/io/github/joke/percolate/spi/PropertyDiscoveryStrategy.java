package io.github.joke.percolate.spi;

import io.github.joke.percolate.model.Property;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

public interface PropertyDiscoveryStrategy {

    Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env);
}
