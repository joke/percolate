# Developer Guide Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create a developer manual for the Objects annotation processor using MkDocs + Material, deployed to GitHub Pages.

**Architecture:** Static documentation site built with MkDocs and the Material theme. Five markdown pages covering getting started, `@Immutable`, `@Mutable`, and a reference section. Deployed via GitHub Actions to GitHub Pages.

**Tech Stack:** MkDocs, Material for MkDocs, Pygments (Java highlighting), GitHub Actions

---

### Task 1: MkDocs project scaffold

**Files:**
- Create: `docs/mkdocs.yml`
- Create: `docs/docs/index.md` (placeholder)

**Step 1: Create `docs/mkdocs.yml`**

```yaml
site_name: Objects
site_description: An annotation processor for generating Java boilerplate
site_url: https://joke.github.io/objects2/

theme:
  name: material
  palette:
    scheme: default
    primary: indigo
    accent: indigo
  features:
    - content.code.copy

nav:
  - Home: index.md
  - Getting Started: getting-started.md
  - '@Immutable': immutable.md
  - '@Mutable': mutable.md
  - Reference: reference.md

markdown_extensions:
  - pymdownx.highlight:
      anchor_linenums: true
  - pymdownx.superfences
  - admonitions
  - pymdownx.tabbed:
      alternate_style: true
```

**Step 2: Create placeholder `docs/docs/index.md`**

```markdown
# Objects

An annotation processor for generating Java boilerplate.
```

**Step 3: Verify the site builds**

Run: `pip install mkdocs-material && cd docs && mkdocs build --strict 2>&1`
Expected: Site builds successfully to `docs/site/`

**Step 4: Add `docs/site/` to `.gitignore`**

Append `docs/site/` to the existing `.gitignore` file (which already has `/.gradle/`, `build/`, `.vscode/`).

**Step 5: Commit**

```bash
git add docs/mkdocs.yml docs/docs/index.md .gitignore
git commit -m "docs: scaffold MkDocs project with Material theme"
```

---

### Task 2: GitHub Actions deployment workflow

**Files:**
- Create: `.github/workflows/docs.yml`

**Step 1: Create `.github/workflows/docs.yml`**

```yaml
name: Deploy docs

on:
  push:
    branches: [main]
    paths: ['docs/**']
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.x'
      - run: pip install mkdocs-material
      - run: cd docs && mkdocs build --strict
      - uses: actions/upload-pages-artifact@v3
        with:
          path: docs/site
      - id: deployment
        uses: actions/deploy-pages@v4
```

**Step 2: Commit**

```bash
git add .github/workflows/docs.yml
git commit -m "ci: add GitHub Actions workflow for docs deployment"
```

---

### Task 3: Home page (`index.md`)

**Files:**
- Modify: `docs/docs/index.md`

**Step 1: Write the home page**

The page should contain:
- A one-sentence description: "Objects is a Java annotation processor that generates implementation classes from annotated interfaces."
- A quick before/after example using `@Immutable` with a `Person` interface that has `getFirstName()` and `getAge()`.
- Navigation links to the other pages.

**Input example** (the user writes this):

```java
package com.example;

import io.github.joke.objects.Immutable;

@Immutable
public interface Person {
    String getFirstName();
    int getAge();
}
```

**Generated output** (Objects produces this at compile time):

```java
package com.example;

public class PersonImpl implements Person {
    private final String firstName;
    private final int age;

    public PersonImpl(String firstName, int age) {
        this.firstName = firstName;
        this.age = age;
    }

    @Override
    public String getFirstName() {
        return this.firstName;
    }

    @Override
    public int getAge() {
        return this.age;
    }
}
```

**Important:** The generated code above is reconstructed from the annotation processor strategies. The `@Override` annotation is shown for clarity since the methods implement the interface — verify the actual processor output by checking the test assertions in `processor/src/test/groovy/io/github/joke/objects/ImmutableProcessorSpec.groovy`. The tests check for `generated.contains(...)` on each fragment, so the exact formatting may differ slightly from what's shown here.

**Step 2: Verify the site builds**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds successfully

**Step 3: Commit**

```bash
git add docs/docs/index.md
git commit -m "docs: write home page with quick example"
```

---

### Task 4: Getting Started page

**Files:**
- Create: `docs/docs/getting-started.md`

**Step 1: Write the Getting Started page**

Content sections:

**Requirements:**
- Java 11 or later

**Gradle setup:**

The project uses a multi-module Gradle layout. Users add two dependencies:
1. `io.github.joke.objects:annotations` — compile-only, provides `@Immutable`, `@Mutable` etc.
2. `io.github.joke.objects:processor` — annotation processor, runs at compile time only.

Example `build.gradle`:

```groovy
dependencies {
    compileOnly 'io.github.joke.objects:annotations:VERSION'
    annotationProcessor 'io.github.joke.objects:processor:VERSION'
}
```

Use tabbed content blocks (`=== "Gradle"` / `=== "Maven"`) if the `pymdownx.tabbed` extension is available. The Maven equivalent:

```xml
<dependency>
    <groupId>io.github.joke.objects</groupId>
    <artifactId>annotations</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.github.joke.objects</groupId>
    <artifactId>processor</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
</dependency>
```

**Note:** Use `VERSION` as a placeholder. There are no published versions yet.

**Step 2: Verify the site builds**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds successfully

**Step 3: Commit**

```bash
git add docs/docs/getting-started.md
git commit -m "docs: write getting started page with Gradle and Maven setup"
```

---

### Task 5: `@Immutable` guide

**Files:**
- Create: `docs/docs/immutable.md`

**Step 1: Write the `@Immutable` guide**

Content sections:

**Overview:** `@Immutable` generates an implementation class with `private final` fields, an all-args constructor, and getter methods.

**Basic example** — single property:

Input:
```java
import io.github.joke.objects.Immutable;

@Immutable
public interface Greeting {
    String getMessage();
}
```

Output:
```java
public class GreetingImpl implements Greeting {
    private final String message;

    public GreetingImpl(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
```

**Multiple properties** — show `Person` with `getFirstName()` + `getAge()`. Same example as home page; keep it brief, link back if needed.

**Boolean properties** — `is*()` prefix:

Input:
```java
@Immutable
public interface Status {
    boolean isActive();
}
```

Output: field `private final boolean active`, getter `public boolean isActive()`, constructor `public StatusImpl(boolean active)`.

**Generated class naming:** The generated class is always `<InterfaceName>Impl` in the same package.

**Step 2: Verify the site builds**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds successfully

**Step 3: Commit**

```bash
git add docs/docs/immutable.md
git commit -m "docs: write @Immutable guide with input/output examples"
```

---

### Task 6: `@Mutable` guide

**Files:**
- Create: `docs/docs/mutable.md`

**Step 1: Write the `@Mutable` guide**

Content sections:

**Overview:** `@Mutable` generates an implementation class with `private` (non-final) fields, both a no-args and an all-args constructor, getter methods, and setter methods.

**Basic example:**

Input:
```java
import io.github.joke.objects.Mutable;

@Mutable
public interface Person {
    String getFirstName();
    int getAge();
}
```

Output:
```java
public class PersonImpl implements Person {
    private String firstName;
    private int age;

    public PersonImpl() {}

    public PersonImpl(String firstName, int age) {
        this.firstName = firstName;
        this.age = age;
    }

    @Override
    public String getFirstName() {
        return this.firstName;
    }

    @Override
    public int getAge() {
        return this.age;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
```

**Key differences from `@Immutable`:**
- Fields are `private` (not `private final`)
- A no-args constructor is always generated
- Setter methods are generated for every property

**Declaring setters in the interface:** Users may optionally declare setters in the interface. If present, they are validated against the getter-derived properties. If the setter name or parameter type does not match, a compilation error occurs. Setters are generated regardless of whether they are declared.

Input:
```java
@Mutable
public interface Person {
    String getFirstName();
    void setFirstName(String firstName); // optional — validated if present
}
```

**Boolean properties:** Same `is*()` convention as `@Immutable`. Setter is `setActive(boolean active)`.

**Step 2: Verify the site builds**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds successfully

**Step 3: Commit**

```bash
git add docs/docs/mutable.md
git commit -m "docs: write @Mutable guide with input/output examples"
```

---

### Task 7: Reference page

**Files:**
- Create: `docs/docs/reference.md`

**Step 1: Write the reference page**

Content sections:

**Annotations table:**

| Annotation | Target | Description |
|------------|--------|-------------|
| `@Immutable` | Interface | Generates immutable implementation with final fields |
| `@Mutable` | Interface | Generates mutable implementation with setters |
| `@ToString` | Interface | Customizes `toString()` generation (style: `STRING_JOINER` or `TO_STRING_BUILDER`) |

**Naming conventions:**

| Method pattern | Property name | Type |
|----------------|--------------|------|
| `getFirstName()` | `firstName` | Return type of getter |
| `isActive()` | `active` | `boolean` |
| `setFirstName(String)` | `firstName` (must match getter) | Parameter type (must match getter return type) |

**Generated class naming:** `<InterfaceName>Impl` in the same package as the source interface.

**Validation rules and error messages:**

For `@Immutable`:

| Rule | Error message |
|------|--------------|
| Must be applied to an interface | `@Immutable can only be applied to interfaces` |
| Methods must have no parameters | `must have no parameters` |
| Methods must not return void | `must not return void` |
| Method names must start with `get` or `is` | `must follow get*/is* naming convention` |

For `@Mutable`:

| Rule | Error message |
|------|--------------|
| Must be applied to an interface | `@Mutable can only be applied to interfaces` |
| Methods must follow `get*/is*/set*` convention | `must follow get*/is*/set* naming convention` |
| Declared setters must match a getter-derived property | `does not match any getter-derived property` |

**Source of truth:** These error messages come from `processor/src/main/java/io/github/joke/objects/strategy/PropertyDiscoveryStrategy.java` (for `@Immutable`) and `processor/src/main/java/io/github/joke/objects/mutable/MutablePropertyDiscoveryStrategy.java` + `SetterValidationStrategy.java` (for `@Mutable`). Cross-check these files if updating the reference.

**Step 2: Verify the site builds**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds successfully

**Step 3: Commit**

```bash
git add docs/docs/reference.md
git commit -m "docs: write reference page with annotations, naming, and validation rules"
```

---

### Task 8: Final verification

**Step 1: Build the full site and verify all pages render**

Run: `cd docs && mkdocs build --strict 2>&1`
Expected: Builds with no warnings or errors

**Step 2: Serve locally and spot-check**

Run: `cd docs && mkdocs serve`
Expected: Site accessible at `http://127.0.0.1:8000/` — verify navigation, code highlighting, and that all five pages load correctly.

**Step 3: Final commit if any fixes needed**

If the build or visual inspection revealed issues, fix them and commit.
