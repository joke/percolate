# GitHub Actions & Repository Settings Design

## Goal

Add CI build workflow and repository-settings app configuration to the Caffeinate project.

## Deliverables

### 1. CI Build Workflow — `.github/workflows/build.yml`

Runs on PRs and pushes to main. Uses mise to install Java from `.mise.toml` (single source of truth for Java version). Uses `gradle/actions/setup-gradle@v4` for Gradle caching. Executes `./gradlew build` (compile + test + ErrorProne/NullAway).

```yaml
name: build

on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: jdkr/setup-mise@v1
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
```

### 2. Repository Settings — `.github/settings.yml`

Adapted from [joke/spock-deepmock](https://github.com/joke/spock-deepmock) baseline.

- Name/description/topics updated for Caffeinate
- Rebase-only merges, auto-delete branches
- Branch protection on main: 1 approval, dismiss stale reviews, code owner reviews
- Required status check: `build` only (no pre-commit.ci, Semantic PR, or DCO)
- Linear history and conversation resolution required

```yaml
repository:
  name: caffeinate
  description: A Java annotation processor that generates implementation classes from annotated interfaces
  topics: java, annotation-processor, code-generation
  private: false
  has_issues: true
  has_projects: false
  has_wiki: false
  has_downloads: true
  default_branch: main
  allow_squash_merge: false
  allow_merge_commit: false
  allow_rebase_merge: true
  allow_auto_merge: true
  allow_update_branch: true
  delete_branch_on_merge: true
  enable_automated_security_fixes: true
  enable_vulnerability_alerts: true

branches:
  - name: main
    protection:
      required_pull_request_reviews:
        required_approving_review_count: 1
        dismiss_stale_reviews: true
        require_code_owner_reviews: true
      required_status_checks:
        strict: true
        checks:
          - context: build
            app_id: 15368
      enforce_admins: false
      required_linear_history: true
      required_conversation_resolution: true
      restrictions:
```

### 3. Existing Docs Workflow — unchanged

`.github/workflows/docs.yml` remains as-is. Deploys MkDocs to GitHub Pages on pushes to main when `docs/**` changes.

## Decisions

- **mise over actions/setup-java**: keeps CI Java version in sync with `.mise.toml`
- **gradle/actions/setup-gradle@v4**: replaces deprecated `gradle-build-action@v2`
- **`./gradlew build` over `check`**: build includes compile + test + checks
- **Only `build` status check required**: other integrations (pre-commit, semantic PR, DCO) not configured for this project
