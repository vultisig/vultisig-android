#!/bin/sh
# Installs project git hooks from scripts/ into .git/hooks/.

HOOKS_DIR="$(git rev-parse --show-toplevel)/.git/hooks"
SCRIPTS_DIR="$(git rev-parse --show-toplevel)/scripts"

install_hook() {
  local name="$1"
  cp "$SCRIPTS_DIR/$name" "$HOOKS_DIR/$name"
  chmod +x "$HOOKS_DIR/$name"
  echo "Installed $name hook."
}

install_hook pre-commit

echo "All hooks installed."
