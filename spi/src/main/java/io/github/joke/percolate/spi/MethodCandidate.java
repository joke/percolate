package io.github.joke.percolate.spi;

import io.github.joke.percolate.spi.types.MethodSig;
import javax.lang.model.element.ExecutableElement;
import lombok.Value;

/**
 * One method a {@link CallableMethods} query offers as a candidate producer: the raw {@code method} (still needed
 * for JavaPoet call-site rendering — {@link CallableMethods#producing} stays on real {@link javax.lang.model.type.TypeMirror}
 * assignability permanently, design D9 amendment), its {@code receiver}, and the owned-model {@link #methodSig} —
 * the adapter-resolved signature (change {@code evict-javax-model}, design D9 transitional bridge), carrying
 * already-resolved parameter/return nullness so a consumer never needs to call the nullness oracle a second time.
 * Always derived by the discovery stage that builds a candidate; no strategy constructs one directly.
 */
@Value
public class MethodCandidate {
    ExecutableElement method;
    Receiver receiver;
    MethodSig methodSig;
}
