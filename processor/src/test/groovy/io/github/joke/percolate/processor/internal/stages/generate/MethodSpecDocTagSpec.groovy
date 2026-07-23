package io.github.joke.percolate.processor.internal.stages.generate

import io.github.joke.percolate.lib.javapoet.MethodSpec
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Modifier

// Pins the percolate MethodSpec overlay (change doc-tag-whole-methods): docTag brackets the WHOLE emitted
// method with AsciiDoc include-tag comments, outside the braces, so a documentation include renders the
// complete method. Exercised over the relocated io.github.joke.percolate.lib.javapoet.MethodSpec, which proves
// the overlay won the shadow duplicate-merge (upstream MethodSpec has no docTag).
@Tag('unit')
class MethodSpecDocTagSpec extends Specification {

    def 'docTag brackets the whole method with AsciiDoc include-tag comments outside the braces'() {
        def method = MethodSpec.methodBuilder('toLocalDateTime')
                .addModifiers(Modifier.PUBLIC)
                .returns(int)
                .addStatement('return 1')
                .docTag('toLocalDateTime')
                .build()

        expect:
        method.toString() == '''\
// tag::toLocalDateTime[]
public int toLocalDateTime() {
  return 1;
}
// end::toLocalDateTime[]
'''
    }

    def 'without docTag the emitted method carries no tag comments'() {
        def method = MethodSpec.methodBuilder('toLocalDateTime')
                .addModifiers(Modifier.PUBLIC)
                .returns(int)
                .addStatement('return 1')
                .build()

        expect:
        method.toString() == '''\
public int toLocalDateTime() {
  return 1;
}
'''
    }
}
