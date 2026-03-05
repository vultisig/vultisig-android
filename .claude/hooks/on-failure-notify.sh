#!/bin/bash
# PostToolUseFailure hook: surface build/lint failure context for self-correction

INPUT=$(cat)
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
ERROR=$(echo "$INPUT" | jq -r '.error // empty' | head -5)

if [ "$TOOL" = "Bash" ]; then
  CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
  if echo "$CMD" | grep -qE '(gradlew|gradle)'; then
    echo "Build/lint failure detected. Command: $CMD" >&2
    echo "Error: $ERROR" >&2
  fi
fi

exit 0
