Implement a Figma design fix with before/after screenshot capture.

## Arguments
- `$ARGUMENTS` - GitHub issue number (e.g., `3524`) or Figma URL

## Workflow
1. Read the issue/URL to get Figma node ID and expected values
2. Fetch Figma design via MCP (`get_design_context` + `get_screenshot`)
3. Capture BEFORE state via PreviewActivity + ADB screencap
4. Implement the code changes matching Figma exactly
5. Rebuild, capture AFTER state via PreviewActivity + ADB screencap
6. Save everything to `screenshots/` for the PR

## Prerequisites
- An emulator must be running (`adb devices` shows a device)
- PreviewActivity exists at `app/src/debug/java/com/vultisig/wallet/debug/PreviewActivity.kt`
- Add a preview case for the target screen if one doesn't exist yet

## After completion
Run `/create-figma-pr` to create a PR with screenshots attached.
