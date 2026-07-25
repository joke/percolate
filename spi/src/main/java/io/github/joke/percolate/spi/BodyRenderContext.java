package io.github.joke.percolate.spi;

import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeMirror;

/**
 * The render context a {@link BodyCodegen} renders against: a superset of {@link IncomingValues} — the same
 * port-keyed incoming expressions — that additionally exposes, per port, the <b>grounded</b> concrete
 * {@link TypeMirror} bound to that port, a {@link ResolveCtx}, the effective {@link SwitchStyle}, and the target
 * {@link SourceVersion}. This lets a conversion whose emitted text depends on the source's shape (e.g. enumerating
 * a source enum's constants) read the grounded source type and choose its rendering, while staying myopic: it
 * exposes only the resolved types of the operation's own ports, never a graph or candidate-{@code Value} snapshot.
 * {@link OperationCodegen#render(IncomingValues)} is unaffected — it continues to receive only the narrower
 * {@link IncomingValues}.
 */
public interface BodyRenderContext extends IncomingValues {

    /** The grounded concrete type bound to the port named {@code portName}. */
    TypeMirror portType(String portName);

    /** The type/member query seam, for a codegen that needs to enumerate or inspect the grounded port type. */
    ResolveCtx resolveCtx();

    /** The raw {@code -Apercolate.switch.style} processor option value; a codegen resolves {@link SwitchStyle#AUTO} itself, against {@link #sourceVersion()}. */
    SwitchStyle switchStyle();

    /** The target {@link SourceVersion} the generated code must compile against. */
    SourceVersion sourceVersion();
}
