Create a PR for Figma implementation work with before/after screenshot verification.

## Arguments
- `$ARGUMENTS` - Optional: issue number to reference in PR

## Pre-flight checks (BLOCKING)
1. Verify `screenshots/before/` has images or text description exists
2. Verify `screenshots/after/` has images or text description exists
3. Verify Figma link is available
4. Build passes (`./gradlew assembleDebug`)
5. No unrelated files in the diff

## If screenshots are missing
- Attempt to capture via Paparazzi or ADB
- If impossible, create a detailed text comparison table (before/after values)
- Add `needs-visual-review` label to the PR

## PR format
- Title: `fix: [description]` (under 70 chars)
- Body: Summary, Figma link, Before/After screenshots, Changes table, Test plan
- Always includes `Fixes #NUMBER` and `Co-Authored-By` trailer
