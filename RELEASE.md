# Release process

This document describes how code in this repository becomes a container image:
the CI/CD phases, when each one runs, what it publishes, and how maintainers cut a
release.

Deployment is out of scope. This repository only builds and publishes images;
composition and deployment live in `inatrace-platform`.

## Summary

| Phase | Trigger | Runs | Publishes |
|---|---|---|---|
| 1. Verify | Pull request to `main` | All tests, image build (no push) | Nothing |
| 2. Main | Merge to `main` | All tests, image build | Dev image `sha-<commit>`, `edge` |
| 3. Prepare release | Manual (maintainer picks the version) | All tests, image build with the release version | Dev image `release-<version>`, draft GitHub Release |
| 4. Publish | Maintainer publishes the draft | Verification, promotion | Official image tags |

Only phase 4 publishes official images and creates git tags.

## Workflows

These are the workflows in the repository's Actions tab, in `.github/workflows/`. Only
one is run by hand.

| Workflow | File | Runs | What it does |
|---|---|---|---|
| **CI** | `ci.yml` | On every pull request and every merge to `main` | Phases 1 and 2: tests, image build, dev image on `main` |
| **Release: prepare (manual)** | `release.yml` | By a maintainer, with the version | Phase 3: tests, release image, draft GitHub Release |
| **Release: publish (on draft published)** | `publish.yml` | When a maintainer publishes the draft | Phase 4: verifies and promotes the image to the official package |
| **Reusable: image** | `_image.yml` | Only from the workflows above | Builds the image and, when asked, pushes and attests it |
| **Reusable: test** | `_test.yml` | Only from the workflows above | Runs the release script tests and `mvn verify` |

Runs are titled after the release they belong to, such as *Prepare release 2.40.1* and
*Publish v2.40.1*.

## Images

| Package | Contents | Who should use it |
|---|---|---|
| `ghcr.io/agstack/inatrace-backend` | Official releases | Everyone |
| `ghcr.io/agstack/inatrace-backend-dev` | Builds from `main` and release builds waiting to be published | Maintainers and testers |

Official tags:

| Release | Tags |
|---|---|
| Final `X.Y.Z` | `X.Y.Z`, `X.Y`; `X` if it is the highest release in major `X`; `latest` if it is the highest release overall |
| Pre-release `X.Y.Z-rc.N` | `X.Y.Z-rc.N` only |

`X.Y.Z` and `X.Y.Z-rc.N` tags never move. `X.Y`, `X` and `latest` move to the newest
matching release.

Dev tags:

| Tag | Meaning |
|---|---|
| `sha-<commit>` | Build of a commit on `main` (never moves) |
| `edge` | Latest build of `main` (moves on every merge) |
| `release-<version>` | Release build waiting for its draft to be published |

## Versioning

- Releases follow [Semantic Versioning](https://semver.org) with git tags `vX.Y.Z`.
  Only tags of this form are considered; anything else is ignored.
- Pre-releases use `vX.Y.Z-rc.N` and are published as GitHub pre-releases.
- **The version is chosen by the maintainer when cutting the release**, not in advance.
  This lets the bump (patch, minor or major) reflect what was actually merged.
- `pom.xml` does not carry the release number. It declares `${revision}`, and CI sets it:
  - release builds: the version being released (`-Drevision=2.40.1`);
  - builds of `main`: `git describe` output, such as `2.40.0-3-ga1b2c3d`
    (3 commits after `v2.40.0`, commit `a1b2c3d`).
- Releasing never commits to `main`. All changes to `main` go through pull requests.

## Phases

### 1. Verify (pull request)

Runs on every pull request to `main`. It is a required check for merging.

1. `mvn verify` runs all tests (unit and integration, using Testcontainers).
2. In parallel, the image is built but not pushed, so a broken `Dockerfile` fails the PR.

### 2. Main (merge)

Runs on every push to `main`, which in practice means every merged pull request.

1. `mvn verify` runs all tests again on the merged result.
2. Once the tests pass, the image is built with the `git describe` version and pushed to
   the dev package as `sha-<commit>` and `edge`, with a provenance attestation. This
   build also writes the [build cache](#build-cache).

The tests run again because the merged result can differ from what the pull request
tested if `main` moved in the meantime.

### 3. Prepare release (manual)

Started by a maintainer, with the version as input.

1. The version is validated:
   - it is valid semver, either `X.Y.Z` or `X.Y.Z-rc.N`;
   - the tag `vX.Y.Z…` does not exist yet;
   - it is higher than every published release in the same `X.Y` line.
2. The current `main` commit is pinned; everything below uses that commit.
3. `mvn -Drevision=<version> verify` runs all tests.
4. The image is built with the version embedded and pushed to the dev package as
   `release-<version>`, with a provenance attestation.
5. A **draft** GitHub Release `v<version>` is created, targeting the pinned commit, with
   generated release notes. Notes for a final release start from the previous final
   release, so that its release candidates do not hide the changes. Versions with `-rc.N`
   are marked as pre-release.

Nothing is public yet. The git tag does not exist until the draft is published.

### 4. Publish (draft published)

Runs when a maintainer publishes the draft (`release: published`). GitHub creates the
tag `v<version>` at that moment.

1. The version is validated again, because another release may have been published
   since the draft was created.
2. The dev image `release-<version>` is located and its attestation is checked: it must
   come from this repository's release workflow, built from the tagged commit.
3. The image is copied **by digest** to the official package with the tags listed in
   [Images](#images). The bytes are not rebuilt: what ships is exactly what phase 3
   tested.
4. A provenance attestation for the official image is created, and the SBOM is attached
   to the GitHub Release.

If the release was not created by phase 3, there is no `release-<version>` image and the
workflow fails with an explicit error. The GitHub Release and its tag still exist, since
the workflow only reacts to the publication: delete both
(`gh release delete v<version> --cleanup-tag`), then cut the release properly.

## Cutting a release

> **Always start from the *Release: prepare (manual)* workflow.** Do not create releases
> or `v*` tags by hand (*Draft a new release*, `gh release create`, `git push --tags`):
> no image is built for them, so publishing fails and the release and tag have to be
> deleted ([Publish](#4-publish-draft-published)). Edit and publish only the drafts the
> workflow creates.

### Pre-release (release candidate)

1. Start the release workflow:
   - **UI:** Actions → *Release: prepare (manual)* → *Run workflow* → `version: 2.40.1-rc.1`
   - **CLI:** `gh workflow run release.yml -R agstack/inatrace-backend -f version=2.40.1-rc.1`
2. When it finishes, review the draft:
   - **UI:** Releases → `v2.40.1-rc.1` → *Edit*
   - **CLI:** `gh release view v2.40.1-rc.1 -R agstack/inatrace-backend`
3. Publish it:
   - **UI:** *Publish release*
   - **CLI:** `gh release edit v2.40.1-rc.1 -R agstack/inatrace-backend --draft=false`
4. Test `ghcr.io/agstack/inatrace-backend:2.40.1-rc.1`.

### Final release

Same steps with `version: 2.40.1`. The final release is rebuilt from the same commit as
the approved release candidate, with the final version embedded, and all tests run
again.

### Discarding a draft

Delete the draft (Releases → draft → *Delete*, or `gh release delete v<version>`). No
tag is created and nothing reaches the official package.

## Example: 2.40.0 → 2.40.1 with a rejected release candidate

Starting point: official `2.40.0`, `2.40`, `2` and `latest` point to the same image.
`…` stands for `ghcr.io/agstack/inatrace-backend`.

| # | Action | Phase | New git tag | New images |
|---|---|---|---|---|
| 1 | Open PR #51 | 1 | — | — |
| 2 | Merge #51 → commit `a1b2c3d` | 2 | — | `…-dev:sha-a1b2c3d`, `…-dev:edge` |
| 3 | Run release with `2.40.1-rc.1` | 3 | — (draft only) | `…-dev:release-2.40.1-rc.1` |
| 4 | Publish the draft | 4 | `v2.40.1-rc.1` → `a1b2c3d` | `…:2.40.1-rc.1` |
| 5 | Testing finds a bug: rc.1 is rejected | — | — | — |
| 6 | PR #52 fixes it → merge → commit `d4e5f6a` | 1, 2 | — | `…-dev:sha-d4e5f6a`, `…-dev:edge` |
| 7 | Run release with `2.40.1-rc.2` | 3 | — (draft only) | `…-dev:release-2.40.1-rc.2` |
| 8 | Publish the draft | 4 | `v2.40.1-rc.2` → `d4e5f6a` | `…:2.40.1-rc.2` |
| 9 | rc.2 is approved; run release with `2.40.1` | 3 | — (draft only) | `…-dev:release-2.40.1` |
| 10 | Publish the draft | 4 | `v2.40.1` → `d4e5f6a` | `…:2.40.1`; `…:2.40`, `…:2`, `…:latest` move to it |

`latest` stays on `2.40.0` until step 10. The rejected `2.40.1-rc.1` stays published as
history; its release notes can say it was superseded by `rc.2`. Running `2.40.1-rc.3`
after step 10 is rejected, because it is lower than `2.40.1`.

## Rationale

- **Version chosen at release time.** Committing the next version in advance (the
  `-SNAPSHOT` convention) forces a guess about whether the next release is a patch or a
  minor. Choosing it when cutting the release removes the guess and the release commits.
- **Version embedded in the build.** The application can report its version (API,
  startup logs), so the release image is built with it. That is why a final release is
  rebuilt instead of reusing the release candidate's image. The release candidate
  validates the commit; the final build runs all tests again.
- **Draft, then publish.** Everything that can fail (tests, build, push) happens before
  anything is public. A maintainer reviews the release notes before publishing.
- **Separate dev package.** The official package only contains releases. Dev images can
  be cleaned up without risk to official tags.
- **Provenance attestation.** A signed statement, keyless via GitHub OIDC, that an image
  digest was built by this repository's workflow from a given commit. Anyone can check
  it with `gh attestation verify oci://<image> -R agstack/inatrace-backend`. Phase 4
  uses it to make sure it promotes the image phase 3 built.
- **SBOM** (Software Bill of Materials). The list of every component inside the image,
  from Java libraries to OS packages, with versions. It answers "is release X affected
  by vulnerability Y?" without pulling the image.

## Build cache

Image builds share a layer cache in the GitHub Actions cache. Only builds of `main`
(phase 2) write it; pull requests and releases read it. Caches written by a pull
request would be visible to that pull request alone, and a release builds a commit that
`main` has already cached. A cache failure never fails a build. Maven dependencies for
the tests are cached separately, by `actions/setup-java`.

Jobs time out after 10 minutes (5 for the short release jobs), so a stuck step fails
quickly instead of holding a runner.

## Repository setup (one time)

- Packages `inatrace-backend` and `inatrace-backend-dev` are public and linked to this
  repository. GHCR creates packages as private on their first push.
- Actions may create releases and write packages. Workflows request only the
  permissions they need: `contents`, `packages`, `id-token`, `attestations`.
- Branch protection on `main`: pull requests only, with the phase 1 checks
  `test / Test` and `image-check / Image` required.

## Testing CI changes in a fork

The workflows do not hard-code names. Images are named after the repository
(`ghcr.io/<owner>/<repo>` and `ghcr.io/<owner>/<repo>-dev`), so a fork or a test copy
publishes to its own packages with no changes. Validate workflow changes end to end in a fork before opening a pull
request here.

[`act`](https://github.com/nektos/act) runs the workflows locally, against a local
registry:

```bash
docker run -d --name ci-registry -p 127.0.0.1:5000:5000 registry:2
act push -W .github/workflows/ci.yml --var IMAGE_REGISTRY=localhost:5000
```

`IMAGE_REGISTRY` replaces `ghcr.io`; it exists only for local runs. Under act
(`ACT=true`) the steps that need GitHub itself are skipped or only printed:
attestations, their verification, and `gh release`. Validate those in a fork.

The release rules (version validation, official tags, release notes start) live in
`.github/scripts/release.py`, tested by `python3 -m unittest discover -s .github/scripts`.

## Known gaps

- **Builds are not deterministic.** Base images and actions are referenced by mutable
  tags, and the jar is not reproducible. Pin images by digest and actions by commit
  SHA, and set `project.build.outputTimestamp`, together with Dependabot to keep the pins
  current.
- **No test against the built image.** Tests run against the code, not the container.
  A smoke test that starts the image and calls the API should run in phase 3.
- **No automated maintenance.** Dependabot, scheduled vulnerability scans and cleanup of
  the dev package are pending. The cleanup must keep `release-*` images that belong to
  open drafts.
- **`amd64` only.** `arm64` images are not built yet.
- **Releases are cut from `main` only.** Maintenance branches for older lines are not
  supported yet.
