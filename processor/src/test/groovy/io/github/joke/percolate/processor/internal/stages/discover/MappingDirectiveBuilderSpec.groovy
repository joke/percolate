package io.github.joke.percolate.processor.internal.stages.discover

import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue

/**
 * {@link MappingDirectiveBuilder} unit-tested on plain {@link RawDirective} data: presence of each optional member is
 * decided against the {@code Map.UNSET} sentinel (an empty string is present, not absent), and the opaque
 * {@link AnnotationValue}/{@link AnnotationMirror} tokens are forwarded untouched — a present member keeps its token
 * (for D6 diagnostic positioning), an absent one carries {@code null}. No javac substrate: the tokens are bare
 * {@code Mock()}s, never stubbed.
 */
@Tag('unit')
class MappingDirectiveBuilderSpec extends Specification {

    static final String UNSET = io.github.joke.percolate.Map.UNSET

    MappingDirectiveBuilder builder = new MappingDirectiveBuilder()

    def 'the always-present target string and its mirror and target-value tokens are carried through'() {
        AnnotationMirror mirror = Mock()
        AnnotationValue targetValue = Mock()
        def raw = raw(target: 'name', mirror: mirror, targetValue: targetValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            target == 'name'
            it.mirror.is(mirror)
            it.targetValue.is(targetValue)
        }
    }

    def 'a source member is present and forwards its token when it is not the sentinel'() {
        AnnotationValue sourceValue = Mock()
        def raw = raw(source: 'in.name', sourceValue: sourceValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasSource()
            source == 'in.name'
            it.sourceValue.is(sourceValue)
        }
    }

    def 'a defaultValue member is present and forwards its token when it is not the sentinel'() {
        AnnotationValue defaultValueValue = Mock()
        def raw = raw(defaultValue: 'unknown', defaultValueValue: defaultValueValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasDefaultValue()
            defaultValue == 'unknown'
            it.defaultValueValue.is(defaultValueValue)
        }
    }

    def 'a constant member is present and forwards its token when it is not the sentinel'() {
        AnnotationValue constantValue = Mock()
        def raw = raw(constant: 'ACTIVE', constantValue: constantValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasConstant()
            constant == 'ACTIVE'
            it.constantValue.is(constantValue)
        }
    }

    def 'an empty-string constant is present, not absent (sentinel, not isEmpty)'() {
        AnnotationValue constantValue = Mock()
        def raw = raw(constant: '', constantValue: constantValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasConstant()
            constant == ''
            it.constantValue.is(constantValue)
        }
    }

    def 'a format member is present and forwards its token when it is not the sentinel'() {
        AnnotationValue formatValue = Mock()
        def raw = raw(format: 'yyyy-MM-dd', formatValue: formatValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasFormat()
            format == 'yyyy-MM-dd'
            it.formatValue.is(formatValue)
        }
    }

    def 'a zone member is present and forwards its token when it is not the sentinel'() {
        AnnotationValue zoneValue = Mock()
        def raw = raw(zone: 'Europe/Berlin', zoneValue: zoneValue)

        expect:
        verifyAll(builder.toDirective(raw)) {
            hasZone()
            zone == 'Europe/Berlin'
            it.zoneValue.is(zoneValue)
        }
    }

    def 'a member left at the sentinel is absent, null-valued, and carries a null token'() {
        def raw = raw(source: 'in.name')

        expect: 'source is present but every unset member is absent with a null value and null token'
        verifyAll(builder.toDirective(raw)) {
            hasSource()
            !hasConstant()
            !hasDefaultValue()
            !hasFormat()
            !hasZone()
            constant == null
            defaultValue == null
            format == null
            zone == null
            constantValue == null
            defaultValueValue == null
            formatValue == null
            zoneValue == null
        }
    }

    private RawDirective raw(final Map args) {
        new RawDirective(
                args.getOrDefault('target', 'name'),
                args.getOrDefault('source', UNSET),
                args.getOrDefault('constant', UNSET),
                args.getOrDefault('defaultValue', UNSET),
                args.getOrDefault('format', UNSET),
                args.getOrDefault('zone', UNSET),
                args.getOrDefault('mirror', Mock(AnnotationMirror)),
                args.getOrDefault('targetValue', Mock(AnnotationValue)),
                args.getOrDefault('sourceValue', Mock(AnnotationValue)),
                args.getOrDefault('constantValue', Mock(AnnotationValue)),
                args.getOrDefault('defaultValueValue', Mock(AnnotationValue)),
                args.getOrDefault('formatValue', Mock(AnnotationValue)),
                args.getOrDefault('zoneValue', Mock(AnnotationValue)))
    }
}
