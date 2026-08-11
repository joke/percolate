// PMD resolves this file's imports only after the package declaration, so it cannot see NullMarked in scope
// and reports UseTypeImports falsely; @SuppressWarnings does not apply to a package declaration.
@NullMarked // NOPMD
package io.github.joke.percolate.docs.extending;

import org.jspecify.annotations.NullMarked;
