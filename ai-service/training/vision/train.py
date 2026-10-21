"""Train a ResNet18 part classifier and save auditable metrics and a live report."""

from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import random
import time
from datetime import datetime, timezone
from pathlib import Path

import torch
from PIL import Image, ImageOps
from torch import nn
from torch.utils.data import DataLoader, Dataset
from torchvision import models, transforms


def log(run, message):
    print(message, flush=True)
    with (run / "training.log").open("a", encoding="utf-8") as file:
        file.write(message + "\n")


class PartImages(Dataset):
    def __init__(self, manifest, split, transform):
        self.root = Path(manifest["root"])
        self.rows = manifest["splits"][split]
        self.labels = {label: index for index, label in enumerate(manifest["classes"])}
        self.transform = transform

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        row = self.rows[index]
        with Image.open(self.root / row["path"]) as image:
            pixels = self.transform(ImageOps.exif_transpose(image).convert("RGB"))
        return pixels, self.labels[row["label"]]


def write_report(run, history, status, metadata, evaluation=None):
    def curve(keys, title):
        if not history:
            return f"<h2>{title}</h2><p>Waiting for the first completed epoch.</p>"
        ceiling = max(1, max(float(row[key]) for row in history for key in keys))
        lines = []
        for key, color in zip(keys, ("#225ea8", "#c84a23")):
            points = " ".join(f"{45 + i * 610 / max(1, len(history)-1):.1f},{235-float(row[key])*190/ceiling:.1f}" for i, row in enumerate(history))
            lines.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="3"/>')
        return f'<h2>{title}</h2><p>Blue: {keys[0]} · Orange: {keys[1]}</p><svg viewBox="0 0 700 270"><path d="M45 35V235H660" fill="none" stroke="#777"/>{"".join(lines)}<text x="45" y="260">Epoch 1</text><text x="590" y="260">Epoch {len(history)}</text><text x="2" y="40">{ceiling:.2f}</text><text x="14" y="235">0</text></svg>'
    columns = ("epoch", "train_loss", "val_loss", "train_accuracy", "val_accuracy", "seconds")
    rows = "".join("<tr>" + "".join(f"<td>{row[key]:.4f}</td>" if isinstance(row[key], float) else f"<td>{row[key]}</td>" for key in columns) + "</tr>" for row in history)
    scores = ""
    if evaluation:
        scores = f'<h2>Held-out test results</h2><p>Top-1: {evaluation["top1"]:.1%} · Top-3: {evaluation["top3"]:.1%} · Images: {evaluation["images"]}</p><p>Tested once after choosing the checkpoint on validation loss.</p>'
        matrix = evaluation["confusion_matrix"]
        scores += '<details><summary>Confusion matrix (rows: actual, columns: predicted)</summary><div style="overflow:auto"><table><tr><th></th>'
        scores += ''.join(f'<th>{html.escape(label)}</th>' for label in metadata["classes"]) + '</tr>'
        for label, counts in zip(metadata["classes"], matrix):
            scores += f'<tr><th>{html.escape(label)}</th>' + ''.join(f'<td>{count}</td>' for count in counts) + '</tr>'
        scores += '</table></div></details>'
    report = f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="refresh" content="10"><title>E-biessa vision training</title><style>body{{font:16px Arial,sans-serif;color:#233747;background:#f4f6f8;max-width:1100px;margin:35px auto;padding:25px}}h1{{margin-bottom:10px}}.cards{{display:grid;grid-template-columns:1fr 1fr;gap:20px}}section{{background:white;padding:20px;border:1px solid #d7dfe5;border-radius:6px}}table{{border-collapse:collapse;background:white;font-size:13px}}td,th{{border:1px solid #d7dfe5;padding:8px}}svg{{width:100%}}.note{{color:#536574;line-height:1.5}}</style></head><body><h1>E-biessa - vision training experiment</h1><p><strong>{html.escape(status)}</strong> | {html.escape(metadata["device"])} | ResNet18 classifier | {len(metadata["classes"])} classes</p><p>Run: {html.escape(run.name)} · Started: {metadata["started_utc"]}</p><p class="note">Dataset: Kaggle 50 Types of Car Parts (gpiosenka). This experiment measures classification on this dataset. Engine-bay performance requires a separate evaluation with mounted-part photos.</p><div class="cards"><section>{curve(("train_loss","val_loss"), "Loss")}</section><section>{curve(("train_accuracy","val_accuracy"), "Accuracy")}</section></div><h2>Actual epoch measurements</h2><table><tr>{''.join(f'<th>{key}</th>' for key in columns)}</tr>{rows}</table>{scores}<p class="note">Exact image duplicates were removed before training. Near duplicates and vehicle/source overlap still need review. Original provider splits are retained.</p></body></html>'''
    (run / "monitor.html").write_text(report, encoding="utf-8")


def evaluate(model, loader, device, count_classes):
    model.eval()
    loss_sum = correct = top3 = total = 0
    matrix = torch.zeros(count_classes, count_classes, dtype=torch.int64)
    with torch.inference_mode():
        for images, targets in loader:
            images, targets = images.to(device), targets.to(device)
            logits = model(images)
            loss_sum += nn.functional.cross_entropy(logits, targets, reduction="sum").item()
            prediction = logits.argmax(1)
            correct += (prediction == targets).sum().item()
            top3 += (logits.topk(min(3, count_classes), 1).indices == targets[:, None]).any(1).sum().item()
            total += len(targets)
            matrix += torch.bincount((targets * count_classes + prediction).cpu(), minlength=count_classes**2).reshape(count_classes, count_classes)
    return {"loss": loss_sum / total, "top1": correct / total, "top3": top3 / total,
            "images": total, "confusion_matrix": matrix.tolist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=Path(__file__).parent / "data/manifest.json")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--workers", type=int, default=0, help="Zero is reliable for Windows.")
    parser.add_argument("--learning-rate", type=float, default=0.001)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.epochs < 1 or args.batch_size < 1 or args.workers < 0 or args.learning_rate <= 0:
        parser.error("Epochs, batch size and learning rate must be positive; workers cannot be negative.")
    random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.set_num_threads(4)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    manifest_bytes = args.manifest.read_bytes()
    manifest = json.loads(manifest_bytes)
    if manifest.get("schema") != 1:
        raise ValueError("Run prepare_data.py first.")
    hashes = [row["sha256_pixels"] for items in manifest["splits"].values() for row in items]
    if len(hashes) != len(set(hashes)):
        raise ValueError("Manifest contains image duplicates; prepare it again.")
    run = args.output or Path(__file__).parent / "runs" / datetime.now().strftime("resnet18-%Y%m%d-%H%M%S")
    run.mkdir(parents=True, exist_ok=False)
    torch.hub.set_dir(str(Path(__file__).parent / "data/pretrained"))
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    weights = models.ResNet18_Weights.DEFAULT
    model = models.resnet18(weights=weights)
    for parameter in model.parameters():
        parameter.requires_grad = False
    model.fc = nn.Linear(model.fc.in_features, len(manifest["classes"]))
    model.to(device)
    optimizer = torch.optim.AdamW(model.fc.parameters(), lr=args.learning_rate)
    transform = transforms.Compose([transforms.RandomResizedCrop(224, scale=(0.8, 1.0)),
        transforms.RandomHorizontalFlip(), transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])])
    loaders = {split: DataLoader(PartImages(manifest, split, transform if split == "train" else weights.transforms()),
        batch_size=args.batch_size, shuffle=split == "train", num_workers=args.workers)
        for split in ("train", "val", "test")}
    metadata = {"started_utc": datetime.now(timezone.utc).isoformat(), "architecture": "resnet18",
        "training_mode": "frozen backbone, trained classification head", "weights": str(weights),
        "classes": manifest["classes"], "seed": args.seed, "epochs": args.epochs,
        "batch_size": args.batch_size, "learning_rate": args.learning_rate,
        "manifest_sha256": hashlib.sha256(manifest_bytes).hexdigest(),
        "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "torch": str(torch.__version__), "device": torch.cuda.get_device_name(0) if device.type == "cuda" else "CPU",
        "split_counts": {split: len(loader.dataset) for split, loader in loaders.items()}}
    (run / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    history = []
    best_loss = float("inf")
    write_report(run, history, "Training starting", metadata)
    log(run, f"DEVICE: {metadata['device']} | RUN: {run}")
    log(run, f"DATA: {metadata['split_counts']} | CLASSIFIER: {len(manifest['classes'])} classes")
    try:
        for epoch in range(1, args.epochs + 1):
            started = time.monotonic()
            # Freeze backbone BatchNorm statistics as well as its parameters.
            model.eval()
            model.fc.train()
            loss_sum = correct = total = 0
            for batch, (images, targets) in enumerate(loaders["train"], 1):
                images, targets = images.to(device), targets.to(device)
                optimizer.zero_grad(set_to_none=True)
                logits = model(images)
                loss = nn.functional.cross_entropy(logits, targets)
                loss.backward()
                optimizer.step()
                loss_sum += loss.item() * len(targets)
                correct += (logits.argmax(1) == targets).sum().item()
                total += len(targets)
                if batch % 50 == 0:
                    log(run, f"Epoch {epoch}/{args.epochs} | batch {batch}/{len(loaders['train'])} | loss {loss_sum/total:.4f}")
            val = evaluate(model, loaders["val"], device, len(manifest["classes"]))
            row = {"epoch": epoch, "train_loss": loss_sum/total, "val_loss": val["loss"],
                   "train_accuracy": correct/total, "val_accuracy": val["top1"], "seconds": time.monotonic()-started}
            history.append(row)
            with (run / "metrics.csv").open("w", newline="", encoding="utf-8") as file:
                writer = csv.DictWriter(file, fieldnames=row.keys())
                writer.writeheader()
                writer.writerows(history)
            if val["loss"] < best_loss:
                best_loss = val["loss"]
                torch.save({"state_dict": model.state_dict(), "classes": manifest["classes"],
                            "epoch": epoch, "validation_loss": best_loss, "metadata": metadata}, run / "best.pt")
            write_report(run, history, f"Epoch {epoch}/{args.epochs} completed", metadata)
            log(run, f"Epoch {epoch}/{args.epochs} | train loss {row['train_loss']:.4f} | val loss {row['val_loss']:.4f} | train acc {row['train_accuracy']:.1%} | val acc {row['val_accuracy']:.1%} | {row['seconds']:.1f}s")
        checkpoint = torch.load(run / "best.pt", map_location=device, weights_only=True)
        model.load_state_dict(checkpoint["state_dict"])
        test = evaluate(model, loaders["test"], device, len(manifest["classes"]))
        test["selected_epoch"] = checkpoint["epoch"]
        (run / "test-results.json").write_text(json.dumps(test, indent=2), encoding="utf-8")
        write_report(run, history, "Training finished", metadata, test)
        log(run, f"TEST | top-1 {test['top1']:.1%} | top-3 {test['top3']:.1%} | selected epoch {checkpoint['epoch']} | images {test['images']}")
    except BaseException as error:
        write_report(run, history, f"Run stopped: {type(error).__name__}", metadata)
        raise


if __name__ == "__main__":
    main()
