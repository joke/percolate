// tag::mapper[]
package examples.extending;

import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.docs.extending.Literal;

// LiteralDirectiveReader and LiteralValue are registered on the annotation-processor classpath — nothing
// else needed to teach percolate a brand-new annotation.
@Mapper
public interface LiteralMapper {

    @Literal(target = "greeting", value = "hello")
    Greeting map();
}
// end::mapper[]
