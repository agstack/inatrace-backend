# Contributing

Contributions are welcome. Questions and design discussion go to the
[INATrace mailing list](https://lists.agstack.org/g/inatrace); bugs and feature
requests go to [GitHub issues](https://github.com/agstack/inatrace-backend/issues).
Report security problems privately, as described in [SECURITY.md](SECURITY.md).

## Setup

You need Java 17, Maven and Docker. [docs/getting-started.md](docs/getting-started.md)
covers running the backend locally.

## Tests

```
mvn verify
```

The tests run against a real MySQL that Testcontainers starts, so Docker must be
running. All of them must pass before a pull request is merged.

- A new or changed endpoint needs a test over the real security filter chain: a
  success case, a user from another company, and an unauthenticated request.
- Add new routes to `docs/api-auth-coverage/routes.tsv`. `ApiRouteInventoryTest`
  fails until you do.
- A schema change needs a migration in `com.abelium.inatrace.db.migrations`; see
  [docs/database.md](docs/database.md).

## Pull requests

- Open an issue first for every change, so it can be discussed before you write
  the code. A pull request without an issue is not merged.
- If the pull request resolves the issue, write `Closes #<issue>` in its
  description so the issue closes when it merges. If it is only part of the
  work, write `Part of #<issue>` instead.
- Branch from `main` and keep each pull request to one change.
- Use [Conventional Commits](https://www.conventionalcommits.org/) for commit
  subjects and pull request titles, and reference the issue:
  `fix: authorize farmer import source documents (#67)`.
- Update the documentation in `docs/` that your change makes wrong, in the same
  pull request.

## Sign your commits

Every commit must carry a [Developer Certificate of Origin](https://developercertificate.org/)
(DCO) sign-off. With it you certify that you wrote the change, or otherwise have
the right to submit it under the project's license. Pull requests with unsigned
commits are not merged.

Add the sign-off with `git commit -s`, which appends a line with the name and
email from your Git configuration:

```
Signed-off-by: Jane Doe <jane@example.com>
```

The email must match the commit author. The sign-off is a person's
certification, so it is always made by a human, also for changes written with
the help of a tool.

To sign off commits you already made, run `git commit --amend --signoff` for
the last one, or `git rebase --signoff main` for every commit on your branch,
then `git push --force-with-lease`.

## License

By contributing, you agree that your contribution is licensed under the same
terms as the rest of the project; see [LICENSE](LICENSE).
