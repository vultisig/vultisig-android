#!/bin/bash
# TaskCompleted hook: verify lint passes before task completion
# Exit 2 to block, exit 0 to allow

cd "$CLAUDE_PROJECT_DIR" || exit 0

MODIFIED=$(git diff --name-only --diff-filter=ACMR HEAD 2>/dev/null | grep -E '\.(kt|xml)$' || true)

if [ -z "$MODIFIED" ]; then
  exit 0
fi

if ! ./gradlew lint 2>&1 | tail -5; then
  echo "Quality gate: lint violations found" >&2
  exit 2
fi

exit 0
