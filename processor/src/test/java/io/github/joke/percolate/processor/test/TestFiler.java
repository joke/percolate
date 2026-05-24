package io.github.joke.percolate.processor.test;

import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.NullMarked;

@NullMarked
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestFiler implements Filer {

    private final FileObject defaultFile;

    public TestFiler(final FileObject defaultFile) {
        this.defaultFile = defaultFile;
    }

    @Override
    public JavaFileObject createSourceFile(final CharSequence canonicalName, final Element... originatingElements)
            throws IOException {
        throw new UnsupportedOperationException("createSourceFile not supported");
    }

    @Override
    public JavaFileObject createClassFile(final CharSequence canonicalName, final Element... originatingElements)
            throws IOException {
        throw new UnsupportedOperationException("createClassFile not supported");
    }

    @Override
    public FileObject createResource(
            final JavaFileManager.Location location,
            final CharSequence packageName,
            final CharSequence relativeName,
            final Element... originatingElements)
            throws IOException {
        return defaultFile;
    }

    @Override
    public FileObject getResource(
            final JavaFileManager.Location location, final CharSequence packageName, final CharSequence relativeName)
            throws IOException {
        throw new UnsupportedOperationException("getResource not supported");
    }
}
