package io.github.joke.percolate.spi;

import javax.lang.model.element.ExecutableElement;

/**
 * A service-loaded SPI role that translates the annotations it owns into {@link DirectiveSink} calls (design D7 of
 * change {@code decouple-engine-from-strategy-semantics}), discovered by {@link java.util.ServiceLoader} exactly as
 * {@link ExpansionStrategy} and {@link SourceProjection} are. A reader is handed one mapper method and SHALL be the
 * only party that reads a user-facing mapping annotation — the {@code processor} module reads none.
 */
public interface DirectiveReader {

    /** Translates {@code method}'s annotations this reader owns into calls on {@code sink}. */
    void read(ExecutableElement method, DirectiveSink sink);
}
