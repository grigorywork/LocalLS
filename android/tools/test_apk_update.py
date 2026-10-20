import unittest
from verify_apk import check_update


class UpdateCompatibilityTest(unittest.TestCase):
    def setUp(self):
        self.previous = dict(package='com.bitpoint.homeservercontrol', versionCode=14, certificates=['certificate-a'])
        self.current = dict(self.previous, versionCode=15)

    def test_same_signer_with_increased_code_is_accepted(self):
        check_update(self.previous, self.current)

    def test_changed_signer_is_rejected(self):
        with self.assertRaises(ValueError):
            check_update(self.previous, dict(self.current, certificates=['certificate-b']))

    def test_changed_package_is_rejected(self):
        with self.assertRaises(ValueError):
            check_update(self.previous, dict(self.current, package='other.app'))

    def test_repeated_or_lower_code_is_rejected(self):
        for code in [14, 13]:
            with self.assertRaises(ValueError):
                check_update(self.previous, dict(self.current, versionCode=code))


if __name__ == '__main__':
    unittest.main(verbosity=2)
