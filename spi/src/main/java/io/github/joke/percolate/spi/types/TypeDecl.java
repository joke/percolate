package io.github.joke.percolate.spi.types;

import java.util.List;
import lombok.Value;

/**
 * A type declaration in a {@link TypeSpace}: its qualified name, its declared type parameters, and its declared
 * supertype edges. An edge is a {@link TypeRef.Declared} whose arguments are expressed in terms of this
 * declaration's own type parameters ({@link TypeRef.Variable}s) — e.g. {@code ArrayList<E>} declares the edge
 * {@code List<E>} — so the assignability walk can substitute a use-site's actual arguments along the edge.
 *
 * <p>SPIKE scope: structure only (no members yet — {@code MethodSig}/{@code FieldSig} land in Phase 1).
 */
@Value
public class TypeDecl {
    String qualifiedName;
    List<String> typeParameters;
    List<TypeRef> superEdges;
}
