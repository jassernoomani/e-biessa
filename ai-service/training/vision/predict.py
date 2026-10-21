"""Test a saved vision classifier on a local photo without changing the app."""

import argparse
import json
from pathlib import Path

import torch
from PIL import Image, ImageOps
from torchvision import models


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--image", type=Path, required=True)
    args = parser.parse_args()
    torch.set_num_threads(4)
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    classes = checkpoint["classes"]
    model = models.resnet18(weights=None)
    model.fc = torch.nn.Linear(model.fc.in_features, len(classes))
    model.load_state_dict(checkpoint["state_dict"])
    model.eval()
    with Image.open(args.image) as image:
        pixels = models.ResNet18_Weights.DEFAULT.transforms()(ImageOps.exif_transpose(image).convert("RGB")).unsqueeze(0)
    with torch.inference_mode():
        scores = model(pixels).softmax(1)[0]
    top = scores.topk(min(5, len(classes)))
    print(json.dumps({"checkpoint": str(args.checkpoint), "image": str(args.image),
        "selected_epoch": checkpoint["epoch"],
        "candidates": [{"part_type": classes[index], "relative_score": round(score, 6)}
                       for score, index in zip(top.values.tolist(), top.indices.tolist())],
        "caution": "Scores are not calibrated probabilities. Whole-image classification does not determine OEM or vehicle compatibility."}, indent=2))


if __name__ == "__main__":
    main()
