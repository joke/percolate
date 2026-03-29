package io.github.joke.percolate.processor

import io.github.joke.percolate.processor.model.MappingMethodModel
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ErrorMessagesSpec extends Specification {

    def 'unknown source property with close match'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.PersonDto' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final method = Stub(ExecutableElement) { toString() >> 'toTarget(PersonDto)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])

        final message = ErrorMessages.unknownSourceProperty('nme', model, ['name', 'age', 'email'] as Set)

        expect:
        message.contains("Unknown source property 'nme'")
        message.contains('toTarget(PersonDto)')
        message.contains('Source type: com.example.PersonDto')
        message.contains('Available source properties: [age, email, name]')
        message.contains('Did you mean: name?')
    }

    def 'unknown source property with no close match'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.PersonDto' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final method = Stub(ExecutableElement) { toString() >> 'toTarget(PersonDto)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])

        final message = ErrorMessages.unknownSourceProperty('zzz', model, ['name', 'age'] as Set)

        expect:
        message.contains("Unknown source property 'zzz'")
        message.contains('Available source properties: [age, name]')
        !message.contains('Did you mean')
    }

    def 'unknown source property caps available properties at 10'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.Big' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final method = Stub(ExecutableElement) { toString() >> 'map(Big)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])
        final available = ('a'..'o').toSet()

        final message = ErrorMessages.unknownSourceProperty('zzz', model, available)

        expect:
        message.contains('and 5 more')
    }

    def 'unknown target property with close match'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.Source' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.PersonRecord' }
        final method = Stub(ExecutableElement) { toString() >> 'toTarget(Source)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])

        final message = ErrorMessages.unknownTargetProperty('givnName', model, ['givenName', 'familyName', 'age'] as Set)

        expect:
        message.contains("Unknown target property 'givnName'")
        message.contains('toTarget(Source)')
        message.contains('Target type: com.example.PersonRecord')
        message.contains('Available target properties: [age, familyName, givenName]')
        message.contains('Did you mean: givenName?')
    }

    def 'unknown target property with no close match'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.Source' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final method = Stub(ExecutableElement) { toString() >> 'toTarget(Source)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])

        final message = ErrorMessages.unknownTargetProperty('zzz', model, ['givenName', 'familyName'] as Set)

        expect:
        message.contains("Unknown target property 'zzz'")
        !message.contains('Did you mean')
    }

    def 'unmapped target property with matching unmapped source'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'com.example.PersonMapper' }

        final message = ErrorMessages.unmappedTargetProperty('firstName', mapperType, ['firstNme', 'suffix'] as Set)

        expect:
        message.contains("Unmapped target property 'firstName'")
        message.contains('com.example.PersonMapper')
        message.contains('Unmapped source properties: [firstNme, suffix]')
        message.contains("Did you mean to map 'firstNme' -> 'firstName'?")
    }

    def 'unmapped target property with no unmapped sources'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'com.example.PersonMapper' }

        final message = ErrorMessages.unmappedTargetProperty('middleName', mapperType, [] as Set)

        expect:
        message.contains("Unmapped target property 'middleName'")
        !message.contains('Unmapped source properties')
        !message.contains('Did you mean to map')
    }

    def 'unmapped target property with no close match among unmapped sources'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'com.example.PersonMapper' }

        final message = ErrorMessages.unmappedTargetProperty('middleName', mapperType, ['zzz'] as Set)

        expect:
        message.contains("Unmapped target property 'middleName'")
        message.contains('Unmapped source properties: [zzz]')
        !message.contains('Did you mean to map')
    }

    def 'conflicting mappings lists source names'() {
        given:
        final mapperType = Stub(TypeElement) { toString() >> 'com.example.PersonMapper' }

        final message = ErrorMessages.conflictingMappings('name', mapperType, ['firstName', 'displayName'] as Set)

        expect:
        message.contains("Conflicting mappings for target property 'name'")
        message.contains('com.example.PersonMapper')
        message.contains('Mapped from: [displayName, firstName]')
    }

    def 'suggestion threshold filters correctly'() {
        given:
        final sourceType = Stub(TypeMirror) { toString() >> 'com.example.Source' }
        final targetType = Stub(TypeMirror) { toString() >> 'com.example.Target' }
        final method = Stub(ExecutableElement) { toString() >> 'map(Source)' }
        final model = new MappingMethodModel(method, sourceType, targetType, [])

        expect:
        // short name "id" (length 2) — threshold is 1, "name" (distance 4) should not match
        !ErrorMessages.unknownSourceProperty('id', model, ['name'] as Set).contains('Did you mean')

        // exact match should suggest
        ErrorMessages.unknownSourceProperty('name', model, ['name'] as Set).contains('Did you mean: name?')

        // case difference "userName" vs "username" (distance 1) should suggest
        ErrorMessages.unknownSourceProperty('userName', model, ['username'] as Set).contains('Did you mean: username?')
    }
}
