"""Tests for release.py. Run with: python3 -m unittest discover -s .github/scripts"""
import unittest

from release import Version, notes_start, published, tags, validate

HISTORY = ["2.21.0", "v2.34.1", "v2.39.0", "v2.40.0", "v2.40.1-rc.1", "v2.40.1-rc.2", "nightly"]


def check(version, history=HISTORY, **kwargs):
    validate(Version(version), published(history), **kwargs)


class ValidateTest(unittest.TestCase):
    def test_accepts_next_versions(self):
        for version in ("2.40.1", "2.40.1-rc.3", "2.41.0", "2.41.0-rc.1", "3.0.0", "2.39.1"):
            check(version)

    def test_rejects_malformed(self):
        for version in ("v2.40.1", "2.40", "2.40.1-beta.1", "2.40.1-rc.0", "02.40.1", "2.40.1-rc1"):
            with self.assertRaises(ValueError):
                check(version)

    def test_rejects_existing_tag(self):
        with self.assertRaises(ValueError):
            check("2.40.1-rc.2")

    def test_rejects_lower_in_same_line(self):
        for version in ("2.40.1-rc.1", "2.40.0-rc.5", "2.39.0"):
            with self.assertRaises(ValueError):
                check(version)

    def test_rejects_candidate_after_final(self):
        with self.assertRaises(ValueError):
            check("2.40.1-rc.3", HISTORY + ["v2.40.1"])

    def test_ignores_tags_without_v(self):
        check("2.21.0")

    def test_published_self(self):
        check("2.40.1", HISTORY + ["v2.40.1"], published_self=True)
        with self.assertRaises(ValueError):
            check("2.40.1", published_self=True)
        with self.assertRaises(ValueError):
            check("2.40.1-rc.1", published_self=True)


class TagsTest(unittest.TestCase):
    def tags(self, version, history=HISTORY):
        return tags(Version(version), published(history))

    def test_final_highest(self):
        self.assertEqual(self.tags("2.40.1"), ["2.40.1", "2.40", "2", "latest"])

    def test_candidate_gets_only_its_tag(self):
        self.assertEqual(self.tags("2.40.1-rc.3"), ["2.40.1-rc.3"])

    def test_hotfix_on_older_line(self):
        history = HISTORY + ["v2.40.1"]
        self.assertEqual(self.tags("2.39.1", history), ["2.39.1", "2.39"])

    def test_older_major(self):
        history = HISTORY + ["v3.0.0"]
        self.assertEqual(self.tags("2.40.1", history), ["2.40.1", "2.40", "2"])

    def test_candidates_do_not_block_floating_tags(self):
        history = HISTORY + ["v2.41.0-rc.1"]
        self.assertEqual(self.tags("2.40.1", history), ["2.40.1", "2.40", "2", "latest"])

    def test_publish_phase_ignores_own_tag(self):
        self.assertEqual(self.tags("2.40.1", HISTORY + ["v2.40.1"]), ["2.40.1", "2.40", "2", "latest"])


class NotesStartTest(unittest.TestCase):
    def start(self, version, history=HISTORY):
        return notes_start(Version(version), published(history))

    def test_final_starts_from_previous_final(self):
        self.assertEqual(self.start("2.40.1"), "v2.40.0")

    def test_candidate_starts_from_previous_release(self):
        self.assertEqual(self.start("2.40.1-rc.3"), "v2.40.1-rc.2")
        self.assertEqual(self.start("2.40.1-rc.1", ["v2.40.0"]), "v2.40.0")

    def test_first_release(self):
        self.assertEqual(self.start("1.0.0", []), "")


if __name__ == "__main__":
    unittest.main()
