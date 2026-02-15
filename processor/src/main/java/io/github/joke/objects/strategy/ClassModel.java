package io.github.joke.objects.strategy;

import com.palantir.javapoet.TypeName;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

public class ClassModel {

    private String className = "";
    private final List<Modifier> modifiers = new ArrayList<>();
    private final List<TypeName> superinterfaces = new ArrayList<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<Modifier> getModifiers() {
        return modifiers;
    }

    public List<TypeName> getSuperinterfaces() {
        return superinterfaces;
    }
}
