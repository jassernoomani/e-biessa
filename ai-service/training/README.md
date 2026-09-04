# Automotive query-extraction training

This folder keeps fine-tuning separate from the runtime FastAPI service. The target is canonical JSON extraction, not memorizing listings or generating compatibility claims.

## Generate deterministic data

From `ai-service`:

```powershell
.\.venv\Scripts\python.exe training\generate_dataset.py
```

The generator creates `training/data/train.json`, `validation.json`, and `test.json`. Training rows contain only LitGPT's documented `instruction`, `input`, and `output` fields. The held-out test rows also contain a language label for evaluation. Fixed English, French, and Tunisian baseline prompts are held out from training.

## Measure the local Ollama baseline

Keep Ollama running, then execute:

```powershell
.\.venv\Scripts\python.exe training\evaluate_ollama.py --model e-biessa-qwen --limit 24
```

The report distinguishes strict canonical accuracy from normalized semantic accuracy. Fine-tuning succeeds only if it improves the held-out strict accuracy without reducing JSON validity.

Recorded pre-training baseline on all 120 held-out examples:

- JSON validity: 100%
- exact full-record accuracy: 0%
- make, model, year, and fuel accuracy: 100% each
- position accuracy: 87.5%
- canonical category accuracy: 8.3%
- canonical part-type accuracy: 13.3%

## Fine-tune with QLoRA in Lightning.ai

The Ollama GGUF is an inference artifact; do not upload it for training. In a Lightning Studio with a CUDA GPU, upload this `training` folder and run:

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip litgpt 'bitsandbytes>=0.43.1'

litgpt finetune_lora Qwen/Qwen2.5-7B-Instruct \
  --data JSON \
  --data.json_path training/data/train.json \
  --data.val_split_fraction 0.1 \
  --data.mask_inputs true \
  --precision bf16-true \
  --quantize bnb.nf4 \
  --devices 1 \
  --train.micro_batch_size 1 \
  --train.global_batch_size 8 \
  --train.max_seq_length 512 \
  --train.epochs 3 \
  --eval.interval 50 \
  --out_dir out/e-biessa-qwen25-7b-qlora
```

Run a short smoke test first by appending `--train.max_steps 10`. If it completes without an out-of-memory error, remove that option and launch the full run. Lightning downloads the original Hugging Face checkpoint in the cloud; the local GGUF remains unchanged.

After training, test the adapter-backed final checkpoint:

```bash
litgpt chat out/e-biessa-qwen25-7b-qlora/final
```

Do not overwrite the baseline report. Export the trained checkpoint to an Ollama-compatible GGUF only after its held-out accuracy is better than the baseline.

## Fine-tune locally on the RTX 4060

From `ai-service`, activate `.venv-training` and validate the dataset without downloading a model:

```powershell
python training\generate_dataset_v2.py
python training\train_local_unsloth.py --train-data training\data\train_v2.json --validation-data training\data\validation_v2.json --validate-only
```

The v2 generator varies years independently, includes requests without a year, preserves `test.json`, and rejects train/validation/test input overlap. Run a 25-step smoke test:

```powershell
python training\train_local_unsloth.py --train-data training\data\train_v2.json --validation-data training\data\validation_v2.json --max-steps 25 --learning-rate 5e-5 --output training\out\corrected-smoke
```

If the smoke test completes, run one corrected epoch. Close games and other GPU-heavy applications first:

```powershell
python training\train_local_unsloth.py --train-data training\data\train_v2.json --validation-data training\data\validation_v2.json --epochs 1 --learning-rate 5e-5 --output training\out\e-biessa-qwen25-7b-v2
```

The full run saves resumable checkpoints every 25 optimizer steps and retains the best validation checkpoint. If it is interrupted, run:

```powershell
python training\train_local_unsloth.py --train-data training\data\train_v2.json --validation-data training\data\validation_v2.json --epochs 1 --learning-rate 5e-5 --output training\out\e-biessa-qwen25-7b-v2 --resume
```

Evaluate a short held-out sample before running the complete test set:

```powershell
python training\evaluate_unsloth.py --adapter training\out\e-biessa-qwen25-7b-v2\final-adapter --limit 12
```

If JSON validity is 100%, evaluate all 120 held-out examples:

```powershell
python training\evaluate_unsloth.py --adapter training\out\e-biessa-qwen25-7b-v2\final-adapter --limit 120
```

`--adapter` is mandatory so an obsolete checkpoint cannot be evaluated accidentally. A repetition penalty is intentionally disabled because extraction must be able to copy make, model, and year tokens directly from the prompt.

Recorded v2 one-epoch result on all 120 original held-out examples:

- JSON validity: 99.2% (119/120)
- make: 99.2%; model: 96.7%
- fuel, position, canonical category, and canonical part type: 99.2% each
- raw year formatting: 0% strict (the model often emits digit strings or duplicates digits)
- hardened full-record accuracy: 96.7% (116/120) after authoritative query-year validation

The runtime must use `merge_model_constraints`; never apply raw model output directly as compatibility filters. Further epochs are not recommended for this dataset—the remaining failures are digit-copy/serialization errors better handled by constrained output and deterministic catalog validation.

The script uses the standard compact Qwen 4-bit checkpoint, a 256-token limit, batch size 1, eight gradient-accumulation steps, rank-8 LoRA, and Unsloth gradient checkpointing to remain within the RTX 4060's 8 GB VRAM. The longest generated training example is 190 Qwen tokens, so this limit does not truncate the current dataset.

## Export and register the trained adapter

The runtime uses Ollama, so convert the final Hugging Face LoRA adapter to GGUF and attach it to the existing `e-biessa-qwen` base model. From `ai-service` with `.venv-training` activated:

```powershell
git clone --depth 1 https://github.com/ggml-org/llama.cpp training\tools\llama.cpp
python training\export_lora_gguf.py
ollama create e-biessa-query-v2 -f training\export\Modelfile
```

The converter downloads the original Qwen model metadata if it is not already in the Hugging Face cache. The generated GGUF and the cloned conversion tools are ignored by Git.

Verify the registered model:

```powershell
ollama run e-biessa-query-v2
```

Ask it to return the extraction JSON for an English, French, or Tunisian automotive query. Exit the interactive prompt with `/bye`. The FastAPI service uses structured output and performs deterministic catalog validation after inference, so a model response can narrow retrieval but cannot invent compatibility data.

## Data policy

- Keep marketplace listing contents out of the model weights; listings belong in Qdrant and are supplied at retrieval time.
- Do not include passwords, messages, phone numbers, emails, or other personal information.
- Preserve the held-out test set. Never add its exact prompts to training data.
- Review machine-generated Tunisian Arabic and automotive terminology with a fluent speaker before the final training run.
