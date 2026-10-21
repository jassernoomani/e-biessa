"""Extract only the 50-class images from the public Kaggle archive."""

import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path, PurePosixPath

from prepare_data import EXTENSIONS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=Path(__file__).parent / "data/car-parts-50.zip")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "data/raw")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    destination = args.output.resolve()
    count = 0
    with zipfile.ZipFile(args.archive) as archive:
        for info in archive.infolist():
            parts = PurePosixPath(info.filename).parts
            if len(parts) != 4 or parts[0] != "car parts 50" or parts[1] not in {"train", "valid", "test"}:
                continue
            if PurePosixPath(info.filename).suffix.lower() not in EXTENSIONS:
                continue
            target = destination.joinpath(*parts[1:]).resolve()
            if not target.is_relative_to(destination) or ".." in parts or "\\" in info.filename:
                raise ValueError(f"Unsafe archive path: {info.filename}")
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output)
            count += 1
    if count == 0:
        raise ValueError("No 50-class images found; inspect the archive structure.")
    with args.archive.open("rb") as archive_file:
        digest = hashlib.file_digest(archive_file, "sha256").hexdigest()
    metadata = {"source": "https://www.kaggle.com/datasets/gpiosenka/car-parts-40-classes",
                "title": "50 Types of Car Parts -Image Classification", "author": "gpiosenka",
                "license_from_kaggle_metadata": "Apache 2.0", "subset": "car parts 50",
                "archive_sha256": digest,
                "images": count}
    (args.output.parent / "source.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(f"Extracted {count} images to {destination}; bundled third-party model files were excluded.")


if __name__ == "__main__":
    main()
