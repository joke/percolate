# Phase Annotations Refactoring Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move `AnalysisPhase`, `GenerationPhase`, and `ValidationPhase` from the `immutable` package to a new dedicated `phase` package so they no longer imply a false ownership by the immutable module.

**Architecture:** Pure structural refactoring — create new files in `io.github.joke.caffeinate.phase`, delete old files from `io.github.joke.caffeinate.immutable`, and update all callers. No logic changes.

**Tech Stack:** Java 11, Gradle, Dagger 2 (qualifier annotations)

---

### Task 1: Create the `phase` package

**Files:**
- Create: `processor/src/main/java/io/github/joke/caffeinate/phase/package-info.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/phase/AnalysisPhase.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/phase/GenerationPhase.java`
- Create: `processor/src/main/java/io/github/joke/caffeinate/phase/ValidationPhase.java`

**Step 1: Create `package-info.java`**

```java
@org.jspecify.annotations.NullMarked
package io.github.joke.caffeinate.phase;
```

**Step 2: Create `AnalysisPhase.java`**

```java
package io.github.joke.caffeinate.phase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface AnalysisPhase {}
```

**Step 3: Create `GenerationPhase.java`**

```java
package io.github.joke.caffeinate.phase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface GenerationPhase {}
```

**Step 4: Create `ValidationPhase.java`**

```java
package io.github.joke.caffeinate.phase;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidationPhase {}
```

**Step 5: Verify compilation**

```bash
./gradlew :processor:compileJava
```

Expected: BUILD SUCCESSFUL (old files still exist, new ones compile alongside them)

---

### Task 2: Update `immutable` package consumers

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableModule.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableGenerator.java`

> **Note:** These files currently use `AnalysisPhase` and `GenerationPhase` with no import
> statement because they live in the same package. After this change they will need
> explicit imports.

**Step 1: Update `ImmutableModule.java`**

Add these two imports (after the existing imports block):

```java
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
```

**Step 2: Update `ImmutableGenerator.java`**

Add these two imports:

```java
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
```

**Step 3: Verify compilation**

```bash
./gradlew :processor:compileJava
```

Expected: BUILD SUCCESSFUL

---

### Task 3: Update `mutable` package consumers

**Files:**
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableModule.java`
- Modify: `processor/src/main/java/io/github/joke/caffeinate/mutable/MutableGenerator.java`

> **Note:** These files already have explicit imports from the old `immutable` package.
> Replace them with imports from the new `phase` package.

**Step 1: Update `MutableModule.java` imports**

Replace:
```java
import io.github.joke.caffeinate.immutable.AnalysisPhase;
import io.github.joke.caffeinate.immutable.GenerationPhase;
import io.github.joke.caffeinate.immutable.ValidationPhase;
```

With:
```java
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
import io.github.joke.caffeinate.phase.ValidationPhase;
```

**Step 2: Update `MutableGenerator.java` imports**

Replace:
```java
import io.github.joke.caffeinate.immutable.AnalysisPhase;
import io.github.joke.caffeinate.immutable.GenerationPhase;
import io.github.joke.caffeinate.immutable.ValidationPhase;
```

With:
```java
import io.github.joke.caffeinate.phase.AnalysisPhase;
import io.github.joke.caffeinate.phase.GenerationPhase;
import io.github.joke.caffeinate.phase.ValidationPhase;
```

**Step 3: Verify compilation**

```bash
./gradlew :processor:compileJava
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Delete old files from `immutable` package

**Files:**
- Delete: `processor/src/main/java/io/github/joke/caffeinate/immutable/AnalysisPhase.java`
- Delete: `processor/src/main/java/io/github/joke/caffeinate/immutable/GenerationPhase.java`
- Delete: `processor/src/main/java/io/github/joke/caffeinate/immutable/ValidationPhase.java`

**Step 1: Delete the three old annotation files**

```bash
rm processor/src/main/java/io/github/joke/caffeinate/immutable/AnalysisPhase.java
rm processor/src/main/java/io/github/joke/caffeinate/immutable/GenerationPhase.java
rm processor/src/main/java/io/github/joke/caffeinate/immutable/ValidationPhase.java
```

**Step 2: Run full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 18 tests passed, 0 failed

**Step 3: Commit**

```bash
git add processor/src/main/java/io/github/joke/caffeinate/phase/ \
        processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableModule.java \
        processor/src/main/java/io/github/joke/caffeinate/immutable/ImmutableGenerator.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableModule.java \
        processor/src/main/java/io/github/joke/caffeinate/mutable/MutableGenerator.java
git rm processor/src/main/java/io/github/joke/caffeinate/immutable/AnalysisPhase.java \
       processor/src/main/java/io/github/joke/caffeinate/immutable/GenerationPhase.java \
       processor/src/main/java/io/github/joke/caffeinate/immutable/ValidationPhase.java
git commit -m "refactor: move phase qualifier annotations to dedicated phase package"
```
