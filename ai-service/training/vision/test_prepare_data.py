import importlib.util
import tempfile
import unittest
from pathlib import Path

from PIL import Image

spec = importlib.util.spec_from_file_location("prepare_data", Path(__file__).with_name("prepare_data.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PreparationTests(unittest.TestCase):
    def make_dataset(self, root):
        index = 0
        for split in ("train", "valid", "test"):
            for label in ("alternator", "water pump"):
                directory = root / split / label
                directory.mkdir(parents=True)
                Image.new("RGB", (8, 8), (index * 30, 15, 20)).save(directory / "one.png")
                index += 1

    def test_train_copy_of_test_image_is_removed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_dataset(root)
            (root / "train/alternator/copy.png").write_bytes((root / "test/alternator/one.png").read_bytes())
            manifest = module.prepare(root, root / "manifest.json")
            self.assertEqual(len(manifest["removed_duplicates"]), 1)
            self.assertEqual(len(manifest["splits"]["train"]), 2)
            hashes = [row["sha256_pixels"] for split in manifest["splits"].values() for row in split]
            self.assertEqual(len(hashes), len(set(hashes)))

    def test_same_image_with_conflicting_labels_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_dataset(root)
            (root / "train/water pump/copy.png").write_bytes((root / "test/alternator/one.png").read_bytes())
            with self.assertRaisesRegex(ValueError, "conflicting labels"):
                module.prepare(root, root / "manifest.json")

    def test_corrupted_image_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_dataset(root)
            (root / "train/alternator/bad.png").write_bytes(b"not an image")
            with self.assertRaisesRegex(ValueError, "Invalid image"):
                module.prepare(root, root / "manifest.json")


if __name__ == "__main__":
    unittest.main()
