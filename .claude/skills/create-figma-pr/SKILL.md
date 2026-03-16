---
name: create-figma-pr
description: Create a PR for Figma implementation work. Verifies before/after screenshots exist, uploads them to the PR, and ensures quality standards are met. Usage: /create-figma-pr
---

# Create Figma Implementation PR

Creates a pull request for Figma design implementation work. Enforces that the developer/implementor has provided before/after screenshots.

## Pre-Flight Checks

### 1. Verify Screenshots Exist

```bash
echo "=== Checking screenshots ==="
BEFORE=$(ls screenshots/before/*.png 2>/dev/null | head -1)
AFTER=$(ls screenshots/after/*.png 2>/dev/null | head -1)

if [ -z "$BEFORE" ]; then
  echo "WARNING: No BEFORE screenshot found in screenshots/before/"
  echo "The implementor must provide a before screenshot."
  echo "Options:"
  echo "  1. Use PreviewActivity + ADB: adb shell am start -n com.vultisig.wallet/.debug.PreviewActivity --es screen '<name>'"
  echo "  2. Use ADB directly: adb shell screencap -p /sdcard/before.png && adb pull /sdcard/before.png screenshots/before/"
  echo "  3. If git history available, checkout previous commit, build, capture, then return"
fi

if [ -z "$AFTER" ]; then
  echo "WARNING: No AFTER screenshot found in screenshots/after/"
  echo "The implementor must capture the current state after changes."
fi
```

### 2. Verify Implementation Summary

```bash
if [ -f /tmp/figma-implementation-summary.md ]; then
  echo "Implementation summary found."
  cat /tmp/figma-implementation-summary.md
else
  echo "WARNING: No implementation summary found."
  echo "Reading from git diff instead."
fi
```

### 3. Verify Code Changes

```bash
# Check that changes exist
git diff --stat
git diff --cached --stat

# Verify no unintended files
git status
```

## Screenshot Upload Strategy

### Option A: Commit Screenshots to Branch (Recommended)

```bash
# Add screenshots to the branch (they'll be in the PR diff)
git add screenshots/before/ screenshots/after/
# These will be visible in the PR as image diffs
```

### Option B: Text-Based Comparison (Last Resort — NO Figma screenshots as substitutes)

If real Android screenshot capture was impossible, use detailed text description.
**NEVER use Figma screenshots as before/after** — they are design references, not Android renders.

```markdown
### Before (text-based — real screenshot unavailable)
- Background: `#12284A` (line 45 of ScreenName.kt)
- Padding: `12.dp` horizontal
- Font size: `14.sp`

### After (text-based — real screenshot unavailable)
- Background: `#11284A` (matches Figma)
- Padding: `16.dp` horizontal (matches Figma)
- Font size: `12.sp` (matches Figma)

### Figma Reference (design target, NOT a screenshot substitute)
[Link to Figma node]

Warning: Real Android screenshots could not be captured. Please verify visually on device.
```

## Create the PR

### Build the PR Body

```bash
# Read the implementation summary
SUMMARY=$(cat /tmp/figma-implementation-summary.md 2>/dev/null || echo "See diff for changes")

# Get the issue number from branch name or summary
ISSUE_NUM=$(git branch --show-current | grep -oE '[0-9]+' | head -1)

# Check for screenshots
HAS_BEFORE=$(test -f screenshots/before/*.png 2>/dev/null && echo "true" || echo "false")
HAS_AFTER=$(test -f screenshots/after/*.png 2>/dev/null && echo "true" || echo "false")
```

### Create PR with Screenshots

```bash
gh pr create --repo vultisig/vultisig-android \
  --title "fix: [brief description matching issue title]" \
  --body "$(cat <<'PR_EOF'
## Summary
Fixes #ISSUE_NUM

[1-3 bullet points describing what changed]

## Screenshots

### Figma (Expected)
[Figma link: https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App?node-id=X-Y]

### Before
[Before screenshot or text description of previous state]

### After
[After screenshot or text description of new state]

## Changes
| Property | Before | After (Figma) |
|----------|--------|---------------|
| [color/size/etc] | [old value] | [new value] |

## Test Plan
- [ ] Visual comparison against Figma design
- [ ] Screen renders correctly on different screen sizes
- [ ] No regressions on adjacent screens
- [ ] Dark mode appearance verified (if applicable)

Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
PR_EOF
)"
```

## Quality Checklist (MUST pass before creating PR)

The PR creator agent MUST verify ALL of these:

### Screenshots
- [ ] **Before screenshot exists** — either PNG file or detailed text description with exact values
- [ ] **After screenshot exists** — either PNG file or detailed text description
- [ ] **Figma reference exists** — link to specific Figma node

### Code Quality
- [ ] **No unrelated changes** — diff only touches files related to the fix
- [ ] **Theme tokens used** — no hardcoded colors that should be theme tokens
- [ ] **Locale files updated** — if any string resources were changed, all 9 locales updated
- [ ] **Existing patterns followed** — code style matches the rest of the file
- [ ] **@Preview updated** — if the screen's preview exists, it reflects the new state

### Verification
- [ ] **Build passes** — `./gradlew assembleDebug` succeeds
- [ ] **Lint passes** — `./gradlew lint` has no new errors
- [ ] **Values match Figma** — every changed value independently verified against Figma design

## Handling Missing Screenshots

If the implementor did NOT provide screenshots:

1. **Try to capture them now**:
   - Check for running emulator: `adb devices | grep -v "List"`
   - If available, use PreviewActivity to render and capture the screen
   - `adb shell am start -n com.vultisig.wallet/.debug.PreviewActivity --es screen "<name>"`
   - `adb shell screencap -p /sdcard/screenshot.png && adb pull /sdcard/screenshot.png screenshots/after/`

2. **If capture is impossible**:
   - Add a detailed **text-based comparison table** in the PR body
   - Include exact hex colors, dp/sp values before and after
   - Add a note: "Warning: Screenshots could not be captured automatically. Please verify visually on device."
   - Add the `needs-visual-review` label to the PR

3. **Block the PR** if:
   - Neither screenshots nor text comparison are available
   - The implementor cannot describe what changed
   - Ask the user to provide screenshots manually

## Rules
- NEVER create a PR without some form of before/after documentation
- Always include the Figma link in the PR body
- Always reference the issue number with `Fixes #NUMBER`
- Always include Co-Authored-By trailer
- Run build and lint before creating PR
- If screenshots are committed to the branch, add them to `.gitignore` reminder for cleanup later
