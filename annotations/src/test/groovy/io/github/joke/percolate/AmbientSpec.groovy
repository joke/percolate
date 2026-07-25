package io.github.joke.percolate

import spock.lang.Specification
import spock.lang.Tag

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Tag('unit')
class AmbientSpec extends Specification {

    def 'is retained until CLASS'() {
        expect:
        Ambient.getAnnotation(Retention).value() == RetentionPolicy.CLASS
    }

    def 'targets parameters only'() {
        expect:
        Ambient.getAnnotation(Target).value() == [ElementType.PARAMETER] as ElementType[]
    }

    def 'value defaults to the empty string'() {
        expect:
        Ambient.getMethod('value').defaultValue == ''
    }
}
