# Security Policy

## Reporting a Vulnerability

If you believe you have found a security vulnerability in the INATrace backend,
please report it privately to **gus@manaiba.tech**.

Please do **not** open a public GitHub issue or pull request for security
problems, and do not disclose the details publicly until a fix has been
released.

### What to include

The more of the following you can provide, the faster we can confirm and fix the
issue:

* A description of the vulnerability and its potential impact.
* The affected component, endpoint or file (for example
  `src/main/java/com/abelium/inatrace/security`).
* The version, tag or commit you tested against.
* Steps to reproduce, ideally a minimal request sequence or proof of concept.
* Any logs, stack traces or screenshots that help us understand the problem.
* Whether authentication was required, and with which role (system admin,
  company admin, regional admin, user).

Please report in English, and let us know if you would like to be credited in
the release notes once the issue is fixed.

### What to expect

* We aim to acknowledge your report within **3 working days**.
* We will send an initial assessment, including whether we can reproduce the
  issue and how we rate its severity, within **10 working days**.
* We will keep you informed of our progress while we work on a fix, and let you
  know when it has been released.

## Supported Versions

Security fixes are applied to the latest release line on the `main` branch
(currently the 2.39.x series). Older releases are not patched; if you are
running an older version, please upgrade before reporting.

## Scope

This policy covers the code in this repository — the INATrace Java backend.

Reports about a specific hosted INATrace deployment should go to the
organisation operating that deployment. If you are unsure, send the report to
the address above and we will route it.

Issues in third-party dependencies should normally be reported to the upstream
project. Tell us anyway if the dependency is reachable through this backend, so
we can plan an upgrade.

### Out of scope

The following are generally not treated as vulnerabilities on their own:

* Findings from automated scanners with no demonstrated impact.
* Missing hardening headers or best practices with no exploitable consequence.
* Denial of service through sheer volume of traffic, and any form of testing
  that degrades a running deployment.
* Social engineering, phishing or physical attacks against users, contributors
  or operators.
* Vulnerabilities that require a fully compromised host, database or developer
  machine.

## Disclosure

We follow coordinated disclosure. Once a fix is available we will publish it,
note the issue in the release notes and, with your agreement, credit you as the
reporter. Please give us a reasonable chance to release a fix before disclosing
the details publicly.
