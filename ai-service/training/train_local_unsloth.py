"""QLoRA fine-tuning for E-biessa automotive constraint extraction."""

from __future__ import annotations

# Unsloth must patch Transformers before anything else imports it.
import unsloth

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
FIELDS = ("brand", "model", "year", "fuelType", "position", "category", "partType")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        default="unsloth/Qwen2.5-7B-Instruct-bnb-4bit",
    )
    parser.add_argument("--train-data", type=Path, default=ROOT / "data" / "train.json")
    parser.add_argument("--validation-data", type=Path, default=ROOT / "data" / "validation.json")
    parser.add_argument("--output", type=Path, default=ROOT / "out" / "e-biessa-qwen25-7b-lora")
    parser.add_argument("--max-steps", type=int, default=-1)
    parser.add_argument("--epochs", type=float, default=1.0)
    parser.add_argument("--learning-rate", type=float, default=5e-5)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--validate-only", action="store_true")
    return parser.parse_args()


def validate_file(path: Path) -> list[dict[str, str]]:
    rows = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(rows, list) or not rows:
        raise ValueError(f"{path} must contain a non-empty JSON array")
    for index, row in enumerate(rows):
        if set(row) != {"instruction", "input", "output"}:
            raise ValueError(f"{path} row {index} has unexpected fields: {sorted(row)}")
        expected = json.loads(row["output"])
        if tuple(expected) != FIELDS:
            raise ValueError(f"{path} row {index} has an invalid output schema")
    return rows


def main() -> int:
    args = arguments()
    train_rows = validate_file(args.train_data)
    validation_rows = validate_file(args.validation_data)
    print(f"Validated {len(train_rows)} training and {len(validation_rows)} validation examples.")
    if args.validate_only:
        return 0

    from unsloth import FastLanguageModel
    import torch
    from datasets import Dataset
    from trl import SFTConfig, SFTTrainer

    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is unavailable. A CUDA-enabled PyTorch build is required.")

    print(f"Loading {args.model} on {torch.cuda.get_device_name(0)}")
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=args.model,
        max_seq_length=256,
        dtype=None,
        load_in_4bit=True,
        # Keep the compact standard 4-bit model entirely on the single GPU.
        # Automatic/sequential placement can incorrectly spill layers to CPU
        # on an 8 GB Windows GPU, which bitsandbytes training does not allow.
        device_map={"": 0},
        use_gradient_checkpointing="unsloth",
        random_state=20260830,
    )
    model = FastLanguageModel.get_peft_model(
        model,
        r=8,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_alpha=16,
        lora_dropout=0,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=20260830,
        max_seq_length=256,
    )

    def prepare(row: dict[str, str]) -> dict[str, str]:
        prompt = tokenizer.apply_chat_template(
            [
                {"role": "system", "content": row["instruction"]},
                {"role": "user", "content": row["input"]},
            ],
            tokenize=False,
            add_generation_prompt=True,
        )
        return {"prompt": prompt, "completion": row["output"] + tokenizer.eos_token}

    train_dataset = Dataset.from_list(train_rows).map(
        prepare, remove_columns=["instruction", "input", "output"], num_proc=1
    )
    validation_dataset = Dataset.from_list(validation_rows).map(
        prepare, remove_columns=["instruction", "input", "output"], num_proc=1
    )
    use_bf16 = torch.cuda.is_bf16_supported()
    evaluation_steps = 10 if args.max_steps > 0 else 25
    warmup_steps = 1 if args.max_steps > 0 else 14
    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        train_dataset=train_dataset,
        eval_dataset=validation_dataset,
        args=SFTConfig(
            output_dir=str(args.output),
            max_length=256,
            dataset_num_proc=1,
            completion_only_loss=True,
            packing=False,
            per_device_train_batch_size=1,
            gradient_accumulation_steps=8,
            per_device_eval_batch_size=1,
            num_train_epochs=args.epochs,
            max_steps=args.max_steps,
            learning_rate=args.learning_rate,
            warmup_steps=warmup_steps,
            lr_scheduler_type="linear",
            optim="adamw_8bit",
            weight_decay=0.01,
            bf16=use_bf16,
            fp16=not use_bf16,
            logging_steps=1,
            eval_strategy="steps",
            eval_steps=evaluation_steps,
            save_strategy="steps",
            save_steps=evaluation_steps,
            save_total_limit=2,
            load_best_model_at_end=True,
            metric_for_best_model="eval_loss",
            greater_is_better=False,
            report_to="none",
            dataloader_num_workers=0,
            seed=20260830,
        ),
    )
    result = trainer.train(resume_from_checkpoint=args.resume)
    trainer.save_model(str(args.output / "final-adapter"))
    tokenizer.save_pretrained(str(args.output / "final-adapter"))
    print(f"Training completed. Loss: {result.training_loss:.6f}")
    print(f"Saved LoRA adapter to {args.output / 'final-adapter'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
