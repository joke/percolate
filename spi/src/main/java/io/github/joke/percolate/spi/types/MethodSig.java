package io.github.joke.percolate.spi.types;

import io.github.joke.percolate.spi.Nullability;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A method's (or constructor's — flagged {@link MemberFlag#CONSTRUCTOR}) model signature: name, declaring
 * type, ordered parameters, return type, <b>resolved</b> return nullness, and the member flags production
 * filters on. All components are values, so signature comparison (e.g. the driver's bind-time self-call rule)
 * is plain {@code equals} — no string keying over {@code TypeMirror.toString()}. Signature values in one
 * mapper round originate from the single adapter walk, so whole-value equality is safe by provenance. The
 * {@link Origin} is diagnostic addressing, not identity — it is excluded from equality.
 */
@Value
public class MethodSig {
    String name;
    String declaredIn;
    List<ParamSig> parameters;
    TypeRef returnType;
    Nullability returnNullness;
    Set<MemberFlag> flags;

    @EqualsAndHashCode.Exclude
    Origin origin;

    /**
     * A compact signature for tests and keying examples: parameters named {@code arg0…}, {@code UNKNOWN}
     * nullness, no flags, empty declaring type, no origin.
     */
    public static MethodSig of(final String name, final TypeRef returnType, final TypeRef... parameterTypes) {
        final List<ParamSig> parameters = IntStream.range(0, parameterTypes.length)
                .mapToObj(i -> new ParamSig("arg" + i, parameterTypes[i], Nullability.UNKNOWN))
                .collect(Collectors.toUnmodifiableList());
        return new MethodSig(name, "", parameters, returnType, Nullability.UNKNOWN, Set.of(), Origin.none());
    }

    /** The ordered parameter types (the overload-identity view of {@link #getParameters()}). */
    public List<TypeRef> parameterTypes() {
        return parameters.stream().map(ParamSig::getType).collect(Collectors.toUnmodifiableList());
    }

    /** Whether this signature carries {@code flag}. */
    public boolean has(final MemberFlag flag) {
        return flags.contains(flag);
    }
}
