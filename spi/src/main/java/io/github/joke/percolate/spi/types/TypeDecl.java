package io.github.joke.percolate.spi.types;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A type declaration in a {@link TypeSpace}: qualified name, kind, declared type parameters, declared
 * supertype edges, and members. An edge is a {@link TypeRef.Declared} whose arguments are expressed in terms
 * of this declaration's own type parameters ({@link TypeRef.Variable}s) — e.g. {@code ArrayList<E>} declares
 * the edge {@code List<E>} — so the assignability walk can substitute a use-site's actual arguments along the
 * edge. Members carry their declaring type ({@code declaredIn}), preserving strategy-side filters (e.g. the
 * {@code java.lang.Object}-member rejection) as plain data checks. The {@link Origin} is diagnostic
 * addressing, not identity — it is excluded from equality.
 */
@Value
public class TypeDecl {
    String qualifiedName;
    DeclKind kind;
    List<String> typeParameters;
    List<TypeRef> superEdges;
    List<MethodSig> methods;
    List<FieldSig> fields;

    @EqualsAndHashCode.Exclude
    Origin origin;

    /** A member-less class declaration — the compact shape algebra tests build. */
    public static TypeDecl of(
            final String qualifiedName, final List<String> typeParameters, final List<TypeRef> superEdges) {
        return of(qualifiedName, typeParameters, superEdges, List.of(), List.of());
    }

    /** A class declaration with members — the compact shape member-enumeration tests build. */
    public static TypeDecl of(
            final String qualifiedName,
            final List<String> typeParameters,
            final List<TypeRef> superEdges,
            final List<MethodSig> methods,
            final List<FieldSig> fields) {
        return new TypeDecl(
                qualifiedName,
                DeclKind.CLASS,
                List.copyOf(typeParameters),
                List.copyOf(superEdges),
                List.copyOf(methods),
                List.copyOf(fields),
                Origin.none());
    }
}
