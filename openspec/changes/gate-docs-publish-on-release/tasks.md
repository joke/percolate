## 1. Move docs/deploy into release.yml

- [x] 1.1 Add a `docs` job to `.github/workflows/release.yml`: `needs: release-please`,
      `if: needs.release-please.outputs.release_created`, checkout at
      `ref: ${{ needs.release-please.outputs.tag_name }}` with `fetch-depth: 0`, run `./gradlew antora`,
      upload the Pages artifact via `actions/upload-pages-artifact@v5` — carry these steps over from
      `build.yml`'s existing `docs` job.
- [x] 1.2 Add a `deploy` job to `.github/workflows/release.yml`: `needs: [release-please, docs]`,
      `if: needs.release-please.outputs.release_created`, `permissions: pages: write, id-token: write`,
      `environment: github-pages`, `concurrency: group: pages, cancel-in-progress: false`, using
      `actions/deploy-pages@v5` — carry over from `build.yml`'s existing `deploy` job.
- [x] 1.3 Update the `publish` job's `needs` in `release.yml` to `[release-please, deploy]` so it runs
      strictly after the Pages deploy succeeds.

## 2. Remove docs/deploy from build.yml

- [x] 2.1 Delete the `docs` and `deploy` jobs from `.github/workflows/build.yml`, leaving only `check`.
- [x] 2.2 Remove the now-unused Pages-related comments/permissions from `build.yml` if nothing else
      references them.

## 3. Update specs

- [x] 3.1 Verify the `user-manual` delta spec at
      `openspec/changes/gate-docs-publish-on-release/specs/user-manual/spec.md` matches the implemented
      workflow (job names, `needs`, `if` conditions).
- [x] 3.2 Verify the `maven-central-publishing` delta spec matches the implemented `publish` job's
      `needs` list.

## 4. Verify

- [x] 4.1 Confirm `.github/workflows/release.yml` and `.github/workflows/build.yml` are valid YAML and
      the job graph (`release-please` → `docs` → `deploy` → `publish`) matches the design's sequence
      diagram.
- [x] 4.2 Run `./gradlew check` — NEVER continue if there are violations.
- [ ] 4.3 Commit the completed change with `/commit-commands:commit`.
