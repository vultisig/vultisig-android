---
name: audit-figma
description: Audit Android screens against Figma designs with two-phase verification. Phase 1 agents find mismatches, Phase 2 agents independently verify before creating issues. Usage: /audit-figma [screen-name-or-figma-url] or /audit-figma all
---

# Figma Design Audit (Two-Phase Verified)

Compare Android Jetpack Compose screens against the Figma source of truth. Uses a two-phase agent workflow to eliminate false positives before creating GitHub issues.

## Figma File Info
- **File key**: `puB2fsVpPrBx3Sup7gaa3v`
- **File URL**: https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App
- **URL format for issues**: `https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App?node-id={nodeId with - instead of :}`

## Figma Section Map

Use `mcp__figma__get_metadata` on node `3:1510` with fileKey `puB2fsVpPrBx3Sup7gaa3v` to get the full page structure. Key sections:

| Section | Node ID | Android Screen(s) |
|---|---|---|
| Homepage | 49057:161765 | screens/home/VaultAccountsScreen.kt |
| Homescreen v3 | 63182:38002 | screens/home/VaultAccountsScreen.kt |
| Vault Selection | 49057:160251 | screens/v2/home/bottomsheets/vaultlist/components/VaultListScreen.kt |
| Add/Edit Folders | 49057:161237 | screens/folder/CreateFolderScreen.kt, FolderScreen.kt |
| Manage Chains | 49057:157270 | screens/ChainSelectionScreen.kt |
| Chain Detail | 49057:159310 | screens/v2/chaintokens/ChainTokensScreen.kt |
| Token Detail | 49057:160028 | screens/TokenDetailScreen.kt |
| Manage Tokens | 50776:145978 | screens/TokenSelectionScreen.kt |
| QR Cards | 49057:158008 | screens/QrAddressScreen.kt |
| Share QR | 65380:109431 | screens/ShareVaultQrScreen.kt |
| Send Flow v3 | 37370:46198 | screens/send/SendFormScreen.kt |
| Verify Send | (in Send section) | screens/send/VerifySendScreen.kt |
| Swap Flow v3 | 33442:54561 | screens/swap/SwapScreen.kt |
| Swap Overview | 67186:146845 | screens/swap/VerifySwapScreen.kt |
| Advanced Swap | 67186:146846 | screens/swap/SwapScreen.kt |
| Swap Limit | 67186:146844 | screens/swap/SwapScreen.kt |
| Onboarding V3 | 60739:32143 | screens/onboarding/OnboardingScreen.kt |
| Amount of Devices | 62435:108575 | screens/v3/onboarding/ChooseDeviceCountScreen.kt |
| Vault Setup | 59111:92965 | screens/v3/onboarding/SetupVaultInfoScreen.kt |
| Import Seed | 58996:160682 | screens/keygen/ImportSeedphraseScreen.kt |
| Fast Vault setup | 60804:29881 | screens/keygen/FastVaultEmailScreen.kt, FastVaultPasswordScreen.kt |
| Secure Vault setup | 60786:186591 | screens/peer/PeerDiscoveryScreen.kt |
| Keygen States | 63140:114517 | screens/keygen/KeygenScreen.kt |
| Settings | 38220:69395 | screens/settings/SettingsScreen.kt |
| Vault Settings | 39598:76673 | screens/vault_settings/VaultSettingsScreen.kt |
| Notifications | 69858:68163 | screens/settings/NotificationsSettingsScreen.kt |
| DeFi Home | 51174:133402 | screens/v2/defi/BaseDeFiPositionsScreen.kt |
| DeFi Thorchain | 51174:135516 | screens/v2/defi/thorchain/ThorchainDefiPositionsScreen.kt |
| DeFi Circle | 57386:132164 | screens/v2/defi/circle/CircleDeFiPositionsScreen.kt |
| Referral | 58680:124717 | screens/referral/ReferralMainScreen.kt |
| Referral Onboarding | 58680:124716 | screens/referral/ReferralOnboardingScreen.kt |
| VULT HODL Tiers | 51817:156821 | screens/settings/DiscountTiersScreen.kt |
| Upgrade Vault | 35806:25984 | screens/reshare/ReshareStartScreen.kt |
| Agent views | 68492:75416 | (vulti agent screens) |
| Scan QR | 66720:110196 | screens/scan/ScanQrScreen.kt |

All paths are relative to `app/src/main/java/com/vultisig/wallet/ui/`.

---

## Two-Phase Audit Architecture

### Overview
```
Phase 1: AUDIT AGENTS (parallel)          Phase 2: REVIEW AGENTS (parallel)
┌─────────────────────┐                   ┌──────────────────────┐
│ Audit Agent 1       │──writes──►        │ Review Agent A       │
│ (Home & Vault)      │          │        │ (verifies group 1+2) │──► Create/Skip Issue
├─────────────────────┤          │        ├──────────────────────┤
│ Audit Agent 2       │──writes──►        │ Review Agent B       │
│ (Chain & Token)     │          │        │ (verifies group 3+4) │──► Create/Skip Issue
├─────────────────────┤    findings.json  ├──────────────────────┤
│ Audit Agent 3       │──writes──►        │ Review Agent C       │
│ (Send Flow)         │          │        │ (verifies group 5+6) │──► Create/Skip Issue
├─────────────────────┤          │        ├──────────────────────┤
│ ...                 │──writes──►        │ ...                  │
└─────────────────────┘                   └──────────────────────┘
```

Issues are ONLY created in Phase 2 after independent verification. Phase 1 agents NEVER create GitHub issues directly.

---

## Phase 1: Audit Agents (Find Candidates)

### Step 1: Determine Scope
- If user provides a specific screen name or Figma URL, audit only that screen (single agent, skip Phase 2)
- If user says "all", launch parallel audit agents (one per group below)
- If user provides a node ID, audit that specific Figma node

### Step 2: Launch Parallel Audit Agents
Each audit agent writes findings to a **shared JSON findings file** instead of creating GitHub issues.

Create the shared findings directory first:
```bash
mkdir -p /tmp/figma-audit-findings
```

**Each Phase 1 agent prompt must include:**

```
## YOUR TASK: AUDIT ONLY — DO NOT CREATE GITHUB ISSUES

You are a Phase 1 AUDIT agent. Your job is to find potential Figma mismatches and write them to a findings file.
DO NOT create GitHub issues. That happens in Phase 2 after review.

## Screens to Audit
[list of screens with Figma node IDs and Android file paths]

## Process for EACH screen:
1. Fetch Figma design: `mcp__figma__get_design_context(fileKey: "puB2fsVpPrBx3Sup7gaa3v", nodeId: "X:Y")`
2. Read the Android .kt screen file
3. Compare EVERY visual property (see comparison table below)
4. Write findings to the shared file

## How to Write Findings
Append each finding as a JSON line to your group's findings file:

```bash
cat >> /tmp/figma-audit-findings/group-N.jsonl <<'FINDING'
{"screen":"ScreenName","component":"ComponentName","figma_node":"X:Y","type":"ui-mismatch","file":"path/to/File.kt","line":123,"what":"Description of mismatch","expected":"Figma value","actual":"Code value","suggested_fix":"Change X to Y","confidence":"high|medium|low","evidence":"How I verified this - exact Figma property name and value"}
FINDING
```

## Confidence Levels
- **high**: Both Figma value and code value independently verified with exact numbers
- **medium**: One side verified exactly, other side inferred from theme tokens or context
- **low**: Speculative or could not fetch Figma node; based on structural analysis only

## Rejection Criteria — DO NOT report these:
- Differences < 2dp/2sp (within acceptable tolerance)
- iOS-specific patterns (home indicators, iOS tab bars, SF Symbols)
- Platform-appropriate adaptations (Android bottom bar vs iOS tab bar)
- Features on active feature branches (check `git branch -a` for work-in-progress)
- Intentionally disabled features (check for `enabled = false` with TODO comments)
- Speculative claims you cannot verify against Figma ("needs verification" = don't report it)
- Color differences that are imperceptible (e.g., #F3F4F5 vs #F0F4FC)
- Same color under different semantic token names (verify resolved hex values match)
```

### Comparison Table (include in each agent prompt)

| Property | Where to find in Figma | Where to find in code |
|---|---|---|
| Background color | Fill color hex | `background()`, `Color()`, theme tokens |
| Text color | Text fill hex | `color =` param, theme tokens |
| Font size | Font size value | `fontSize = X.sp`, style references |
| Font weight | Font weight name/number | `fontWeight = FontWeight.X` |
| Padding | Auto layout padding | `.padding(X.dp)` |
| Gap/spacing | Auto layout item spacing | `Arrangement.spacedBy(X.dp)`, `Spacer(height=X.dp)` |
| Corner radius | Corner radius values | `RoundedCornerShape(X.dp)`, `.clip()` |
| Border | Stroke weight + color | `border(X.dp, color, shape)` |
| Icon size | Frame width/height | `size(X.dp)`, `Modifier.size()` |
| Opacity | Layer opacity | `.alpha(X)`, `Color.copy(alpha=X)` |

### Parallel Audit Groups

Launch 9 parallel agents, one per group:

1. **Home & Vault**: Homepage, Vault Selection, Search, Folders, Vault Detail
2. **Chain & Token**: Manage Chains, Chain Detail, Token Detail, Manage Tokens, QR Cards, Share QR
3. **Send Flow**: Send Form, Verify Send, Gas Settings, Pairing Device, Keysign, Address Book
4. **Swap Flow**: Swap Screen, Verify Swap, Advanced Swap, Swap Limit, Asset Selection
5. **Keygen & Onboarding**: Onboarding, Device Count, Vault Setup, Import Seed, Fast/Secure Vault, Keygen
6. **Settings**: Settings, Vault Settings, Backup, Notifications, Discount Tiers, Reshare
7. **DeFi**: DeFi Home, THORChain, Maya, Circle, TON, DYDX, TRON, Functions
8. **Referral & Misc**: Referral, NFTs, Transaction History, Scan QR, Sign Message, Migration
9. **Agent & Notifications**: Vulti Agent, Notifications, Banners

---

## Phase 2: Review Agents (Verify & Create Issues)

**Wait for ALL Phase 1 agents to complete before launching Phase 2.**

### Step 3: Collect All Findings
```bash
cat /tmp/figma-audit-findings/group-*.jsonl > /tmp/figma-audit-findings/all-findings.jsonl
wc -l /tmp/figma-audit-findings/all-findings.jsonl  # total candidate count
```

### Step 4: Launch Parallel Review Agents
Split the findings file into chunks and launch review agents. Each review agent independently verifies findings from a DIFFERENT audit agent (cross-verification).

**Each Phase 2 agent prompt must include:**

```
## YOUR TASK: VERIFY FINDINGS AND CREATE ISSUES

You are a Phase 2 REVIEW agent. Audit agents found potential mismatches. Your job is to
INDEPENDENTLY VERIFY each finding before creating a GitHub issue. You must check both
the Figma design AND the code yourself — do not trust the audit agent's claims.

## Findings to Review
[paste the JSONL findings assigned to this agent]

## Review Process for EACH Finding

1. Read the finding's claimed values
2. Fetch the Figma design yourself: `mcp__figma__get_design_context(fileKey: "puB2fsVpPrBx3Sup7gaa3v", nodeId: "X:Y")`
3. Read the Android code at the referenced file:line yourself
4. Compare independently — does the mismatch actually exist?

## Verdict for Each Finding

### REJECT if:
- The claimed Figma value is wrong (you see a different value in Figma)
- The claimed code value is wrong (code actually uses the correct value)
- The finding is speculative with confidence "low" and you cannot verify it
- The difference is < 2dp/2sp (tolerance threshold)
- Same hex color under different token names
- iOS-specific pattern not applicable to Android
- Feature exists on a feature branch (check with `git branch -a | grep -i keyword`)
- Platform-appropriate adaptation (e.g., Android bottom bar positioning)
- Figma node is for a different screen than what was compared

→ Log rejection: `echo "REJECT #N: [reason]" >> /tmp/figma-audit-findings/review-results.txt`

### APPROVE and create GitHub issue if:
- You independently confirmed both the Figma value AND the code value
- The mismatch is real and visually meaningful
- The finding has correct file path, line number, and node ID

→ Create the issue:
```bash
gh issue create \
  --repo vultisig/vultisig-android \
  --title "UI Mismatch: [Component] - [Brief description]" \
  --label "ui-mismatch" \
  --body "$(cat <<'ISSUE_EOF'
## Figma vs Implementation Mismatch

**Screen**: [Screen name]
**Component**: [Component name]
**Figma Link**: https://www.figma.com/design/puB2fsVpPrBx3Sup7gaa3v/Vultisig-App?node-id=[nodeId-with-dashes]
**Verified by**: Phase 1 audit + Phase 2 independent review

### What's Wrong
[Describe the specific mismatch]

### Expected (Figma)
[Exact values from Figma - hex colors, dp/sp sizes — verified independently]

### Actual (Android)
[Exact values from code with file path and line number — verified independently]

### Suggested Fix
[Concrete code change]
ISSUE_EOF
)"
```

→ Log approval: `echo "APPROVE #N: [issue-url]" >> /tmp/figma-audit-findings/review-results.txt`

### NEEDS CLARIFICATION if:
- Figma MCP rate limit hit and you cannot verify
- The finding is plausible but you need design team input

→ Log: `echo "UNCLEAR #N: [reason]" >> /tmp/figma-audit-findings/review-results.txt`
```

For missing features, the review agent should:
- Search all branches: `git branch -a | grep -i keyword`
- Search codebase broadly: files, routes, viewmodels under different names
- Check if intentionally deferred (TODO comments, disabled flags)

### Step 5: Summarize Results
After all review agents complete, read the results:
```bash
echo "=== APPROVED ===" && grep "^APPROVE" /tmp/figma-audit-findings/review-results.txt | wc -l
echo "=== REJECTED ===" && grep "^REJECT" /tmp/figma-audit-findings/review-results.txt | wc -l
echo "=== UNCLEAR ===" && grep "^UNCLEAR" /tmp/figma-audit-findings/review-results.txt | wc -l
```

---

## Single Screen Mode (skip Phase 2)

When auditing a single screen (user provides URL or screen name), run both phases inline:

1. Fetch Figma design context
2. Read Android code
3. Compare properties
4. For each potential finding, verify it yourself before creating an issue
5. Apply the same rejection criteria as Phase 2

---

## Global Rules

### Quality Gates (findings MUST pass ALL to become issues)
1. **Figma evidence**: Exact value from Figma with property name (not "I think" or "should be")
2. **Code evidence**: Exact value from code with file path and line number
3. **Materiality**: Difference is visually perceptible (>= 2dp/2sp, or clearly different color)
4. **Correct comparison**: Figma node is for the same screen/component being compared
5. **Platform relevance**: Not an iOS-specific pattern on Android
6. **Not in progress**: Feature is not on an active branch (check `git branch -a`)

### Common False Positive Patterns to Avoid
- **Same color, different token**: `backgrounds.secondary` and `backgrounds.surface1` can resolve to the same hex
- **iOS home indicator**: Bottom dots in Figma are iOS system UI, not app UI
- **Tab bars**: iOS uses bottom tab bars, Android uses different navigation patterns
- **Backdrop blur**: Not natively supported on Android — not a per-screen issue
- **Inner shadows on buttons**: Systemic design system gap, not per-screen issue
- **Height from Figma**: Many components use auto-layout; height is derived, not fixed
- **Disabled/hidden features**: Items with `enabled = false` are intentionally hidden pending implementation

### Issue Labels
Before creating issues, ensure labels exist:
```bash
gh label create ui-mismatch --color "FF6B6B" --description "Visual mismatch between Figma design and implementation" 2>/dev/null
gh label create missing-feature --color "FFA500" --description "Feature exists in Figma but not implemented" 2>/dev/null
```

### Figma URL Construction
Replace `:` with `-` in node IDs: node `49057:161765` → URL param `node-id=49057-161765`
