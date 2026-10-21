"""Audit an ImageFolder dataset and create a manifest without split leakage."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

from PIL import Image, ImageOps

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def prepare(root: Path, output: Path) -> dict:
    root = root.resolve()
    folders = {"train": root / "train", "val": root / "valid", "test": root / "test"}
    if not folders["val"].is_dir():
        folders["val"] = root / "val"
    if not all(folder.is_dir() for folder in folders.values()):
        raise ValueError("Dataset root must contain train/, valid/ (or val/), and test/.")
    classes = sorted(folder.name for folder in folders["train"].iterdir() if folder.is_dir())
    if len(classes) < 2:
        raise ValueError("At least two classes are required.")
    for split, folder in folders.items():
        if set(classes) != {entry.name for entry in folder.iterdir() if entry.is_dir()}:
            raise ValueError(f"{split} class folders do not match the training labels.")

    seen = {}
    rows = {split: [] for split in folders}
    removed = []
    # Retain held-out examples when the same pixels also appear in training.
    for split in ("test", "val", "train"):
        for label in classes:
            for path in sorted((folders[split] / label).iterdir()):
                if path.suffix.lower() not in EXTENSIONS:
                    continue
                try:
                    with Image.open(path) as image:
                        image = ImageOps.exif_transpose(image).convert("RGB")
                        image.load()
                        digest = hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest()
                except (OSError, ValueError) as error:
                    raise ValueError(f"Invalid image {path}: {error}") from error
                if digest in seen:
                    previous = seen[digest]
                    if previous["label"] != label:
                        raise ValueError(f"Duplicate pixels have conflicting labels: {path} and {previous['path']}")
                    removed.append({"path": str(path.relative_to(root)), "split": split,
                                    "same_as": previous["path"]})
                    continue
                item = {"path": str(path.relative_to(root)), "label": label, "sha256_pixels": digest}
                seen[digest] = item
                rows[split].append(item)

    counts = {split: dict(Counter(row["label"] for row in items)) for split, items in rows.items()}
    for split, items in counts.items():
        if set(items) != set(classes):
            raise ValueError(f"{split} has a class with no unique images; inspect the dataset.")
    result = {"schema": 1, "root": str(root), "classes": classes, "splits": rows,
              "counts": counts, "removed_duplicates": removed,
              "limitations": "Pixel duplicates removed. Near duplicates and same-vehicle/source leakage require manual review."}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"Classes: {len(classes)} | " + " | ".join(f"{split}: {len(items)}" for split, items in rows.items()))
    print(f"Removed duplicate images: {len(removed)} | Manifest: {output}")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data/manifest.json")
    args = parser.parse_args()
    prepare(args.data, args.output)
