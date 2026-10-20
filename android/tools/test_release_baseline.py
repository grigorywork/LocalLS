import unittest
from verify_apk import check_release_baseline

class ReleaseBaselineTest(unittest.TestCase):
    def test_explicit_baseline(self):
        check_release_baseline(dict(versionCode=18, releaseBaselineVersionCode="18"), True, None, True)

    def test_cannot_skip_update_checks_in_later_versions(self):
        with self.assertRaises(ValueError):
            check_release_baseline(dict(versionCode=19, releaseBaselineVersionCode="18"), True, None, True)
        with self.assertRaises(ValueError):
            check_release_baseline(dict(versionCode=19), True, None, False)

    def test_baseline_requires_signer_and_no_previous_apk(self):
        for required, previous in [(False, None), (True, "old.apk")]:
            with self.assertRaises(ValueError):
                check_release_baseline(dict(versionCode=18, releaseBaselineVersionCode="18"), required, previous, True)

if __name__ == '__main__': unittest.main()
