#!/usr/bin/env python3
"""Release version rules shared by the release and publish workflows (see RELEASE.md).

Published releases are the git tags vX.Y.Z and vX.Y.Z-rc.N; any other tag is ignored.

  release.py validate VERSION [--published-self] TAG...
      Fails unless VERSION is X.Y.Z or X.Y.Z-rc.N, its tag is not published yet and it
      is higher than every published release in the same X.Y line. With
      --published-self, VERSION's own tag is expected to exist already (publish phase).
  release.py tags VERSION TAG...
      Prints the official image tags for VERSION, one per line.
  release.py notes-start VERSION TAG...
      Prints the tag release notes should start from, or nothing for the first release.
"""
import re
import sys

PATTERN = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?$")


class Version:
    def __init__(self, text):
        match = PATTERN.match(text)
        if not match:
            raise ValueError(f"'{text}' is not X.Y.Z or X.Y.Z-rc.N")
        self.text = text
        self.major, self.minor, self.patch = (int(g) for g in match.groups()[:3])
        self.rc = int(match.group(4)) if match.group(4) else None

    @property
    def final(self):
        return self.rc is None

    @property
    def key(self):
        # Semver precedence: a release candidate sorts before its final release.
        return (self.major, self.minor, self.patch, 0 if self.rc else 1, self.rc or 0)

    def __lt__(self, other):
        return self.key < other.key

    def __eq__(self, other):
        return self.key == other.key

    def __hash__(self):
        return hash(self.key)


def published(tags):
    versions = []
    for tag in tags:
        if tag.startswith("v"):
            try:
                versions.append(Version(tag[1:]))
            except ValueError:
                pass
    return versions


def validate(version, releases, published_self=False):
    if published_self:
        if version not in releases:
            raise ValueError(f"tag v{version.text} is not published")
        releases = [r for r in releases if r != version]
    elif version in releases:
        raise ValueError(f"tag v{version.text} already exists")
    line = [r for r in releases if (r.major, r.minor) == (version.major, version.minor)]
    higher = [r.text for r in line if not r < version]
    if higher:
        raise ValueError(f"{version.text} is not higher than published {', '.join(sorted(higher))}")


def tags(version, releases):
    if not version.final:
        return [version.text]
    finals = [r for r in releases if r.final and r != version]
    result = [version.text, f"{version.major}.{version.minor}"]
    if all(r < version for r in finals if r.major == version.major):
        result.append(str(version.major))
    if all(r < version for r in finals):
        result.append("latest")
    return result


def notes_start(version, releases):
    # A final release lists everything since the previous final release, so its own
    # release candidates do not hide the changes; a candidate starts from any release.
    earlier = [r for r in releases if r < version and (r.final or not version.final)]
    return f"v{max(earlier, key=lambda r: r.key).text}" if earlier else ""


def main(argv):
    if len(argv) < 2 or argv[0] not in ("validate", "tags", "notes-start"):
        sys.exit(__doc__)
    command, args = argv[0], argv[1:]
    published_self = "--published-self" in args
    args = [a for a in args if a != "--published-self"]
    try:
        version = Version(args[0])
        releases = published(args[1:])
        if command == "validate":
            validate(version, releases, published_self)
        elif command == "tags":
            print("\n".join(tags(version, releases)))
        else:
            print(notes_start(version, releases))
    except ValueError as error:
        sys.exit(f"error: {error}")


if __name__ == "__main__":
    main(sys.argv[1:])
