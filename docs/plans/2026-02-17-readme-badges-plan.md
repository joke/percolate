# README, Badges & Repo Polish Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add LICENSE file, update README with Caffeinate branding and badges, link GitHub Pages on the repo, and fix the docs site URL.

**Architecture:** Four static file changes — a new LICENSE file, updated README.md, updated settings.yml (add homepage), and updated mkdocs.yml (fix site_url). No code changes.

**Tech Stack:** Markdown, YAML, Apache 2.0 license text

---

### Task 1: Add Apache 2.0 LICENSE file

**Files:**
- Create: `LICENSE`

**Step 1: Create the LICENSE file**

Use the standard Apache License 2.0 text from https://www.apache.org/licenses/LICENSE-2.0.txt with copyright line: `Copyright 2025 joke`

**Step 2: Commit**

```bash
git add LICENSE
git commit -m "chore: add Apache 2.0 license"
```

---

### Task 2: Update README.md with Caffeinate branding and badges

**Files:**
- Modify: `README.md`

**Step 1: Replace README.md contents**

```markdown
# Caffeinate

[![build](https://github.com/joke/caffeinate/actions/workflows/build.yml/badge.svg)](https://github.com/joke/caffeinate/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A Java annotation processor that generates implementation classes from annotated interfaces.

[Documentation](https://joke.github.io/caffeinate/)
```

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: update README with Caffeinate branding and badges"
```

---

### Task 3: Add homepage to settings.yml and fix docs site_url

**Files:**
- Modify: `.github/settings.yml` (add `homepage` field after `description`)
- Modify: `docs/mkdocs.yml` (change `site_url`)

**Step 1: Add homepage to settings.yml**

Add this line after the `description` field in the `repository` section:

```yaml
  homepage: https://joke.github.io/caffeinate/
```

**Step 2: Fix site_url in mkdocs.yml**

Change line 3 from:

```yaml
site_url: https://joke.github.io/objects2/
```

to:

```yaml
site_url: https://joke.github.io/caffeinate/
```

**Step 3: Commit**

```bash
git add .github/settings.yml docs/mkdocs.yml
git commit -m "chore: add homepage URL and fix docs site_url"
```
