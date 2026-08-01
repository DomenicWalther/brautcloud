#!/usr/bin/env python3
"""Fail CI when tracked files contain high-confidence credential material."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

JWT = re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")
PRIVATE_KEY = re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")
AWS_ACCESS_KEY = re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")
GITHUB_TOKEN = re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b")
SECRET_ASSIGNMENT = re.compile(
    r"(?i)(?:password|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)"
    r"\s*[:=]\s*[\"']?([A-Za-z0-9][A-Za-z0-9_./+=:@-]{11,})(?=[\"'}\s,]|$)"
)
CONFIG_SUFFIXES = (
    ".bru",
    ".conf",
    ".env",
    ".ini",
    ".json",
    ".properties",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
)
SAFE_MARKERS = (
    "change-me",
    "dummy",
    "example",
    "local-only",
    "placeholder",
    "replace-me",
    "test-",
    "test_",
)


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], check=True, stdout=subprocess.PIPE
    )
    return [Path(name) for name in result.stdout.decode().split("\0") if name]


def is_safe_placeholder(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in SAFE_MARKERS)


def main() -> int:
    findings: list[tuple[Path, int, str]] = []
    for path in tracked_files():
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue

        for line_number, line in enumerate(text.splitlines(), 1):
            if JWT.search(line) or PRIVATE_KEY.search(line) or AWS_ACCESS_KEY.search(line):
                findings.append((path, line_number, "high-confidence credential material"))
            elif GITHUB_TOKEN.search(line):
                findings.append((path, line_number, "high-confidence GitHub token"))

            if path.name.startswith(".env") or path.name.endswith(CONFIG_SUFFIXES):
                match = SECRET_ASSIGNMENT.search(line)
                if match and not is_safe_placeholder(match.group(1)):
                    findings.append((path, line_number, "hardcoded secret-like value"))

    if findings:
        print("Secret scan failed:", file=sys.stderr)
        for path, line_number, reason in findings:
            print(f"  {path}:{line_number}: {reason}", file=sys.stderr)
        return 1

    print("Secret scan passed: no high-confidence credentials found in tracked files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
