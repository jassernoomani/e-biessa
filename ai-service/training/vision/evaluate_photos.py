"""Compare saved classifier and the app's CLIP prompts on external photos."""

import argparse
import ast
import hashlib
import html
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

import torch
from PIL import Image, ImageOps
from torchvision import models
from transformers import CLIPModel, CLIPProcessor


def ranking(scores, labels):
    top = scores.topk(min(5, len(labels)))
    return [{"label": labels[index], "relative_score": round(score, 6)}
            for score, index in zip(top.values.tolist(), top.indices.tolist())]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--dataset-manifest", type=Path, default=Path(__file__).parent / "data/manifest.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cases = json.loads(args.cases.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / "images").mkdir()
    torch.set_num_threads(4)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    classes = checkpoint["classes"]
    classifier = models.resnet18(weights=None)
    classifier.fc = torch.nn.Linear(classifier.fc.in_features, len(classes))
    classifier.load_state_dict(checkpoint["state_dict"])
    classifier.to(device).eval()
    transform = models.ResNet18_Weights.DEFAULT.transforms()

    service = Path(__file__).resolve().parents[2]
    taxonomy_ast = ast.parse((service / "app/vision_taxonomy.py").read_text(encoding="utf-8"))
    taxonomy = next(ast.literal_eval(node.value) for node in taxonomy_ast.body
                    if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == "PART_TAXONOMY" for target in node.targets))
    clip_labels = [label for label, _ in taxonomy]
    cache = str(service / "data/vision-models")
    clip = CLIPModel.from_pretrained("openai/clip-vit-base-patch32", cache_dir=cache, local_files_only=True).to(device).eval()
    processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32", cache_dir=cache, local_files_only=True)
    prompts = [f"a clear product photo of an automotive {label}" for label in clip_labels]
    manifest = json.loads(args.dataset_manifest.read_text(encoding="utf-8"))
    known_hashes = {row["sha256_pixels"]: {"split": split, "label": row["label"], "path": row["path"]}
                    for split, rows in manifest["splits"].items() for row in rows}

    def predict(image):
        with torch.inference_mode():
            scores = classifier(transform(image).unsqueeze(0).to(device)).softmax(1)[0]
            inputs = processor(text=prompts, images=image, return_tensors="pt", padding=True)
            inputs = {key: value.to(device) for key, value in inputs.items()}
            baseline = clip(**inputs).logits_per_image.softmax(1)[0]
        return {"classifier": ranking(scores, classes), "clip": ranking(baseline, clip_labels)}

    results = []
    for case in cases:
        path = Path(case["path"])
        with Image.open(path) as source:
            image = ImageOps.exif_transpose(source).convert("RGB")
        digest = hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest()
        result = {**case, "image_dimensions": image.size, "dataset_pixel_overlap": known_hashes.get(digest),
                  "full": predict(image)}
        if case.get("crop"):
            box = tuple(round(fraction * image.size[index % 2]) for index, fraction in enumerate(case["crop"]))
            result["crop_pixels"] = box
            result["crop_predictions"] = predict(image.crop(box))
        shutil.copy2(path, args.output / "images" / f"{case['id']}.jpg")
        results.append(result)
        print(f"Photo {case['id']}: CLIP={result['full']['clip'][0]['label']} | trained={result['full']['classifier'][0]['label']} | dataset overlap={result['dataset_pixel_overlap']}", flush=True)
        if result.get("crop_predictions"):
            print(f"  Crop: CLIP={result['crop_predictions']['clip'][0]['label']} | trained={result['crop_predictions']['classifier'][0]['label']}", flush=True)

    with args.checkpoint.open("rb") as file:
        checkpoint_digest = hashlib.file_digest(file, "sha256").hexdigest()
    report = {"created_utc": datetime.now(timezone.utc).isoformat(), "checkpoint_sha256": checkpoint_digest,
              "classes": classes, "clip_classes": clip_labels, "photos_used_for_training": False,
              "caution": "Small diagnostic sample, not a held-out accuracy estimate. Labels are visual interpretations; ambiguous components require confirmation. Scores use different candidate sets and are not calibrated or directly comparable.",
              "results": results}
    (args.output / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    def cell(items):
        return "<br>".join(f"{html.escape(item['label'])}: {item['relative_score']:.1%}" for item in items[:3])
    rows = []
    for result in results:
        crop = result.get("crop_predictions")
        extra = f"<br><strong>Crop:</strong><br>{cell(crop['classifier'])}" if crop else ""
        overlap = html.escape(str(result["dataset_pixel_overlap"])) if result["dataset_pixel_overlap"] else "No exact pixel match in dataset"
        rows.append(f"<tr><td><strong>Photo {result['id']}</strong><br><img src='images/{result['id']}.jpg' alt='Photo {result['id']}'></td><td>{html.escape(result['expected'])}<br><small>{html.escape(result['note'])}<br>{overlap}</small></td><td>{cell(result['full']['clip'])}</td><td>{cell(result['full']['classifier'])}{extra}</td></tr>")
    document = f"""<!doctype html><html lang='en'><meta charset='utf-8'><title>E-biessa photo comparison</title><style>body{{font:15px Arial,sans-serif;max-width:1250px;margin:30px auto;color:#233747;padding:20px}}table{{border-collapse:collapse;width:100%}}td,th{{border:1px solid #cbd4dc;padding:12px;text-align:left;vertical-align:top}}img{{width:155px;height:115px;object-fit:contain}}small{{display:block;margin-top:8px;color:#526270;max-width:280px}}td{{line-height:1.55}}</style><h1>E-biessa - external photo comparison</h1><p>Current CLIP versus ResNet18 pilot, evaluated on {len(results)} user-supplied photos.</p><p>{html.escape(report['caution'])}</p><p>The originals and manually specified alternator crops were evaluated without training on these photos. Full crop predictions and pixel-overlap checks are recorded in results.json.</p><table><tr><th>Photo</th><th>Visual interpretation / limitations</th><th>Current CLIP - top 3</th><th>Trained classifier - top 3</th></tr>{''.join(rows)}</table></html>"""
    (args.output / "index.html").write_text(document, encoding="utf-8")


if __name__ == "__main__":
    main()
