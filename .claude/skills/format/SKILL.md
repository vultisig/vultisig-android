---
name: format
description: Remove unused imports and format staged Kotlin files. Run before /commit to clean up code. Automatically downloads ktlint if not installed.
disable-model-invocation: true
allowed-tools: Bash
---

# Format Staged Kotlin Files

Remove unused imports and auto-format all staged `.kt` files using `ktlint` before committing.

## Step 1 — Get staged Kotlin files

```bash
git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$'
```

If there are no staged `.kt` files, print "No staged Kotlin files to format." and stop.

## Step 2 — Ensure ktlint is available

Check if `ktlint` is on the PATH:

```bash
which ktlint 2>/dev/null
```

If not found, download it into the project-local `.ktlint/` directory (one-time setup):

```bash
mkdir -p .ktlint
curl -sSLO --output-dir .ktlint \
  https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint
chmod +x .ktlint/ktlint
echo "ktlint downloaded to .ktlint/ktlint"
```

Resolve the binary path:

```bash
KTLINT=$(which ktlint 2>/dev/null || echo ".ktlint/ktlint")
```

## Step 3 — Format each staged file

Run ktlint with `--format` on all staged Kotlin files. This removes unused imports and applies the standard Kotlin style:

```bash
STAGED=$(git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$')
echo "$STAGED" | xargs $KTLINT --format 2>&1
```

If ktlint exits with an error that is **not** about fixable style violations, print the output and stop. Formatting errors (exit code 1 when only auto-fixable issues are found) are expected and fine.

## Step 4 — Re-stage the formatted files

Re-add the files so the formatted versions are included in the commit:

```bash
echo "$STAGED" | xargs git add
```

## Step 5 — Show summary

```bash
echo ""
echo "Formatted and re-staged Kotlin files:"
echo "$STAGED" | sed 's/^/  ✓ /'
echo ""
echo "Ready to /commit"
```

## Notes

- `ktlint --format` handles `no-unused-imports`, indentation, spacing, trailing commas, and all standard Kotlin style rules.
- `.ktlint/` is gitignored by default if `.gitignore` has `/.ktlint/` — add it if it isn't already there.
- If your project gains a `ktlint` Gradle task later, replace Step 2–3 with `./gradlew ktlintFormat`.
