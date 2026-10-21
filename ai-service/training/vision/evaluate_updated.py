"""Exercise the actual expanded identification service on reviewed local photos."""

import argparse
import html
import json
import sys
from PIL import Image, ImageOps
from pathlib import Path

SERVICE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(SERVICE))
from app.config import Settings
from app.vision_identifier import VisionPartIdentifier
from app.vision_taxonomy import PART_TAXONOMY


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    identifier = VisionPartIdentifier(Settings(ai_service_token="local-vision-evaluation-token-only", vision_enabled=True,
        vision_cache_dir=SERVICE / "data/vision-models",
        vision_classifier_checkpoint=SERVICE / "training/vision/runs/pilot-20261014/best.pt"))
    results = []
    for case in json.loads(args.cases.read_text(encoding="utf-8")):
        response = identifier.identify(Path(case["path"]).read_bytes()).model_dump()
        result = {**case, "response": response}
        if identifier.ready:
            import torch
            with Image.open(case["path"]) as source:
                pixels = ImageOps.pad(ImageOps.exif_transpose(source).convert("RGB"), (224, 224),
                                      method=Image.Resampling.LANCZOS, color=(127, 127, 127))
            inputs = identifier._processor(images=pixels, return_tensors="pt")
            with torch.inference_mode():
                features = identifier._model.get_image_features(**{key: value.to(identifier._device) for key, value in inputs.items()})
                if not isinstance(features, torch.Tensor):
                    features = features.pooler_output
                features = features / features.norm(dim=-1, keepdim=True)
                similarity = (features @ identifier._text_features.T)[0]
                scores = (similarity * identifier._model.logit_scale.exp()).softmax(0)
                order = scores.topk(3).indices.tolist()
                result["clip_diagnostics"] = [{"label": PART_TAXONOMY[i][0] if i < len(PART_TAXONOMY) else "non-part / scene",
                                               "score": float(scores[i]), "similarity": float(similarity[i])} for i in order]
        results.append(result)
        print(f"{case['id']}: {response['status']} | {[candidate['partType'] for candidate in response['candidates']]} | expected: {case['expected']}", flush=True)
    (args.output / "results.json").write_text(json.dumps({"taxonomy_size": len(PART_TAXONOMY),
        "thresholds": {"score": 0.35, "margin": 0.12, "similarity": 0.20, "similarity_margin": 0.01, "untrained_score": 0.85},
        "note": "Diagnostic evaluation, not statistical calibration. No evaluation photos were added to training.",
        "results": results}, indent=2), encoding="utf-8")
    rows = ''.join(f"<tr><td>{html.escape(str(row['id']))}</td><td>{html.escape(row['expected'])}</td><td>{row['response']['status']}</td><td>{html.escape(', '.join(candidate['partType'] for candidate in row['response']['candidates']) or 'No part claimed')}</td><td>{html.escape(row['response']['caution'])}</td></tr>" for row in results)
    document = f"""<!doctype html><meta charset='utf-8'><title>Expanded vision evaluation</title><style>body{{font:16px Arial;max-width:1250px;margin:30px auto;padding:20px;color:#233747}}table{{border-collapse:collapse}}td,th{{border:1px solid #ccd5dd;padding:10px;text-align:left}}td{{line-height:1.5}}</style><h1>E-biessa - expanded vision evaluation</h1><p>{len(PART_TAXONOMY)} part labels, trained classifier support, and uncertain-result handling.</p><p>Zero-shot labels broaden category coverage. The trained classifier still has its original 50 classes. These are diagnostic photos, not a calibrated benchmark.</p><table><tr><th>Photo</th><th>Reviewed interpretation</th><th>Status</th><th>Suggested part</th><th>Advice</th></tr>{rows}</table>"""
    (args.output / "index.html").write_text(document, encoding="utf-8")


if __name__ == "__main__":
    main()
