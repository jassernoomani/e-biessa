"""Export a trained PEFT/LoRA adapter as an Ollama-compatible GGUF adapter."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--adapter",
        type=Path,
        default=ROOT / "out" / "e-biessa-qwen25-7b-v2" / "final-adapter",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "export" / "e-biessa-qwen25-7b-v2-lora.gguf",
    )
    parser.add_argument("--base-model", default="Qwen/Qwen2.5-7B-Instruct")
    parser.add_argument(
        "--converter",
        type=Path,
        default=ROOT / "tools" / "llama.cpp" / "convert_lora_to_gguf.py",
    )
    return parser.parse_args()


def main() -> int:
    args = arguments()
    if not (args.adapter / "adapter_config.json").is_file():
        raise FileNotFoundError(f"LoRA adapter not found at {args.adapter}")
    if not args.converter.is_file():
        raise FileNotFoundError(
            f"llama.cpp converter not found at {args.converter}. "
            "Clone it with: git clone --depth 1 https://github.com/ggml-org/llama.cpp.git training/tools/llama.cpp"
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            sys.executable,
            str(args.converter),
            str(args.adapter),
            "--outfile",
            str(args.output),
            "--outtype",
            "f16",
            "--base-model-id",
            args.base_model,
        ],
        check=True,
    )
    print(f"Exported LoRA GGUF: {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
