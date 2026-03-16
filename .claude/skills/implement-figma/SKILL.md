---
name: implement-figma
description: Implement a Figma design change with before/after screenshot capture. Takes a GitHub issue or Figma URL, fetches the design, captures a "before" screenshot, implements the fix, captures an "after" screenshot, and prepares everything for PR. Usage: /implement-figma <issue-number-or-figma-url>
---

# Implement Figma Design Change

Implements a UI fix based on a Figma design with verified before/after screenshots.

## Workflow Overview
```
1. Parse input (issue# or Figma URL)
2. Fetch Figma design (design reference only — NOT a screenshot substitute)
3. Capture BEFORE screenshot via PreviewActivity + ADB
4. Implement the code changes
5. Rebuild, capture AFTER screenshot via PreviewActivity + ADB
6. Save before/after PNGs for PR attachment
```

## CRITICAL: Screenshot Rules
- **Before/After screenshots MUST be real Android renders** — via PreviewActivity + ADB screencap on the emulator
- **Figma screenshots are NOT before/after screenshots** — Figma is only a design reference to know what the target looks like
- **Never substitute a Figma screenshot for a before or after image** — the whole point is showing the actual Android rendering changed
- If real screenshots cannot be captured, the PR MUST clearly state this and request manual visual review on device

---

## Step 1: Parse Input

**If GitHub issue number**: Read the issue to extract:
- Figma node ID and link
- Screen/component name
- File path and line numbers
- Expected vs actual values
```bash
gh issue view NUMBER --repo vultisig/vultisig-android
```

**If Figma URL**: Extract fileKey and nodeId from URL pattern:
- `figma.com/design/:fileKey/:fileName?node-id=:nodeId` → convert `-` to `:` in nodeId

## Step 2: Fetch Figma Design (Expected State)

Get the design spec and screenshot:
```
mcp__figma__get_design_context(
  fileKey: "puB2fsVpPrBx3Sup7gaa3v",
  nodeId: "<extracted-node-id>",
  clientLanguages: "kotlin",
  clientFrameworks: "jetpack-compose"
)
```

Also capture a Figma screenshot separately:
```
mcp__figma__get_screenshot(
  fileKey: "puB2fsVpPrBx3Sup7gaa3v",
  nodeId: "<extracted-node-id>"
)
```

Save the Figma screenshot for PR reference (design target only).

## Step 3: Capture BEFORE Screenshot

### Primary Method: PreviewActivity + ADB (Preferred)

The project has a debug-only `PreviewActivity` at `app/src/debug/java/com/vultisig/wallet/debug/PreviewActivity.kt` that renders composable previews with mock data on a real device/emulator.

#### 3a. Add a preview case for the target screen

1. Open `PreviewActivity.kt` and add a new `when` branch for the screen:
```kotlin
when (screen) {
    "swap_confirm" -> SwapConfirmPreview()
    "settings" -> SettingsPreview()
    "<screen_name>" -> <ScreenName>Preview()   // ← add this
    else -> SwapConfirmPreview()
}
```

2. Create a `@Composable` preview function with realistic mock data:
```kotlin
@Composable
private fun <ScreenName>Preview() {
    // Build mock state with realistic values
    val state = <ScreenName>UiModel(
        // ... fill in with representative data
    )
    <ScreenName>Screen(
        state = state,
        // ... provide no-op callbacks
    )
}
```

**Mock data guidelines**:
- Use real `Coins.*` objects (e.g., `Coins.Ethereum.ETH`, `Coins.Bitcoin.BTC`) for token data
- Use realistic amounts and fiat values (e.g., "1.5 ETH", "$3,847.50")
- Fill in all required fields — empty/default data makes screenshots useless for comparison
- The inner stateless composable (not the Hilt one) must be `internal` visibility to be callable from the debug source set

#### 3b. Build and install

```bash
./gradlew :app:installDebug
```

#### 3c. Launch and capture

```bash
# Launch the preview
adb shell am start -n com.vultisig.wallet/.debug.PreviewActivity --es screen "<screen_name>"

# Wait for render, then capture
sleep 2
mkdir -p screenshots/before
adb shell screencap -p /sdcard/screenshot_before.png
adb pull /sdcard/screenshot_before.png screenshots/before/
```

### Fallback: Direct ADB Navigation

If the screen is easy to reach in the running app (e.g., Settings, Home):

```bash
# Launch the app
adb shell am start -n com.vultisig.wallet/.app.activity.MainActivity

# Navigate via taps (use `adb shell uiautomator dump` to find element bounds)
adb shell uiautomator dump /sdcard/ui_dump.xml
adb pull /sdcard/ui_dump.xml /tmp/ui_dump.xml
# Parse bounds, then tap
adb shell input tap X Y

# Capture
sleep 2
mkdir -p screenshots/before
adb shell screencap -p /sdcard/screenshot_before.png
adb pull /sdcard/screenshot_before.png screenshots/before/
```

### Last Resort: No Emulator Available

If no emulator is running (`adb devices` shows no devices):

1. **Do NOT use Figma screenshots as before/after** — they are design references, not Android renders
2. Note clearly in the PR that real screenshots could not be captured
3. Provide a detailed text-based comparison table with exact values from code (before → after)
4. Add `needs-visual-review` label to the PR — a human must verify on device

## Step 4: Implement the Code Changes

Read the target file and make the changes based on the Figma design context.

### Implementation Checklist
- [ ] Match exact hex colors from Figma (verify against theme tokens)
- [ ] Match exact dp/sp values for spacing, sizing, typography
- [ ] Match corner radii, border widths, opacity values
- [ ] Use existing theme tokens where they map to Figma values (`Theme.v2.colors.*`, `Theme.brockmann.*`)
- [ ] For colors not in theme, define as `private val` constants at top of file
- [ ] Follow existing code patterns in the file
- [ ] Do NOT change unrelated code
- [ ] If string resources change, update ALL 9 locale files (en, de, es, hr, it, nl, pt, ru, zh-rCN)

### Common Theme Token Mappings
```kotlin
// Check these files for token → hex mappings:
// app/src/main/java/com/vultisig/wallet/ui/theme/V2Theme.kt
// app/src/main/java/com/vultisig/wallet/ui/theme/BrockmannTheme.kt
```

## Step 5: Capture AFTER Screenshot

1. Rebuild and reinstall:
```bash
./gradlew :app:installDebug
```

2. Launch PreviewActivity and capture (same screen name as before):
```bash
adb shell am start -n com.vultisig.wallet/.debug.PreviewActivity --es screen "<screen_name>"
sleep 2
mkdir -p screenshots/after
adb shell screencap -p /sdcard/screenshot_after.png
adb pull /sdcard/screenshot_after.png screenshots/after/
```

## Step 6: Prepare for PR

Save real Android screenshots:
```
screenshots/
├── before/          ← REAL Android render BEFORE code changes
│   └── screen.png
└── after/           ← REAL Android render AFTER code changes
    └── screen.png
```

These are ACTUAL Android renders, not Figma exports. The Figma design is referenced by link only.

Create a summary for the PR creator:
```bash
cat > /tmp/figma-implementation-summary.md <<'EOF'
## Implementation Summary

**Issue**: #NUMBER
**Screen**: ScreenName
**Figma Link**: https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App?node-id=X-Y

### Changes Made
- [List each change with before → after values]

### Screenshots (Real Android Renders)
- `screenshots/before/` — Android render before changes (ADB screencap)
- `screenshots/after/` — Android render after changes (ADB screencap)
- Figma design: [link above] (reference only, not a screenshot)

### Files Modified
- `path/to/File.kt` (lines X-Y)
EOF
```

Inform the user: "Implementation complete. Run `/create-figma-pr` to create a PR with before/after screenshots attached."

---

## PreviewActivity Reference

Location: `app/src/debug/java/com/vultisig/wallet/debug/PreviewActivity.kt`
Manifest: `app/src/debug/AndroidManifest.xml`

The PreviewActivity is a debug-only activity that:
- Takes a `screen` extra via intent to select which preview to render
- Wraps content in `OnBoardingComposeTheme` (the app's actual theme)
- Uses mock data with real `Coins.*` objects for realistic rendering
- Is only included in debug builds — never ships to production

To add a new screen preview, edit `PreviewActivity.kt`:
1. Add a new `when` branch in `onCreate`
2. Create a `@Composable` function with mock data
3. Call the screen's inner stateless composable (must be `internal` visibility)

## Rules

- ALWAYS fetch the Figma design before writing any code
- ALWAYS attempt to capture before screenshots before making changes
- Match Figma EXACTLY — do not approximate values
- Use theme tokens when they resolve to the correct Figma hex value
- When modifying strings, update all 9 locale files
- Do not modify unrelated code
- If the screen's inner composable is `private`, change it to `internal` so PreviewActivity can call it
- Ensure PreviewActivity changes are committed alongside the fix (they're debug-only, harmless)
