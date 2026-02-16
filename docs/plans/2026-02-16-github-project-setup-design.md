# GitHub Project Setup & Rename to Caffeinate — Design

## Goal

Rename the project from "Objects" to "Caffeinate" (including Java internals) and set up GitHub project infrastructure: README, CI build workflow, Dependabot, and repository settings via probot/settings.

## Task Order

Rename first, then layer GitHub setup on top of the renamed codebase.

1. Full rename to Caffeinate
2. Update README.md
3. Build workflow (GitHub Actions + mise + Gradle)
4. Dependabot configuration
5. Repository settings (probot/settings)

## 1. Full Rename

**Scope:**

| What | From | To |
|---|---|---|
| `settings.gradle` rootProject.name | `objects` | `caffeinate` |
| `build.gradle` group | `io.github.joke.objects` | `io.github.joke.caffeinate` |
| Java packages | `io.github.joke.objects.*` | `io.github.joke.caffeinate.*` |
| Annotation classes | `io.github.joke.objects.Immutable` etc. | `io.github.joke.caffeinate.Immutable` etc. |
| Source directories | `src/main/java/io/github/joke/objects/` | `src/main/java/io/github/joke/caffeinate/` |
| Test imports | `io.github.joke.objects.*` | `io.github.joke.caffeinate.*` |
| Docs site | `site_name: Objects` | `site_name: Caffeinate` |
| Docs code examples | `io.github.joke.objects.*` imports | `io.github.joke.caffeinate.*` imports |
| CLAUDE.md | All references | Updated |

**Verification:** `./gradlew build` must pass after rename. `cd docs && mkdocs build --strict` must pass.

## 2. README.md

Concise README:
- Title: `# Caffeinate`
- One-paragraph description
- Badge row: build status, license
- Quick `@Immutable` input → output example
- Link to docs site

## 3. Build Workflow

File: `.github/workflows/build.yml`

- Triggers: push to `main`, pull requests
- Uses `jdkr/setup-mise` to install mise (reads `.mise.toml` for Java version)
- Runs `./gradlew build`
- Job name: `build` (matches required status check in settings.yml)

## 4. Dependabot

File: `.github/dependabot.yml`

- `github-actions` ecosystem: weekly, `chore` commit prefix
- `gradle` ecosystem: weekly, `chore` commit prefix
- No ignore rules

## 5. Repository Settings

File: `.github/settings.yml`

Based on spock-deepmock, adapted:
- Repo name, description, topics updated for Caffeinate
- Merge policy: rebase only, auto-merge, delete branch on merge
- Security: automated fixes, vulnerability alerts
- Branch protection on `main`:
  - 1 required approving review, dismiss stale, require code owner
  - Strict status checks with `build` context only (app_id: 15368)
  - Linear history required, conversation resolution required
  - Admins not enforced
