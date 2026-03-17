Audit Android screens against the Figma design file for visual mismatches using a two-phase verified workflow.

## Arguments
- `$ARGUMENTS` - Can be: a Figma URL, a screen name, "all" for full audit, or a specific Figma node ID

## Two-Phase Workflow

This audit uses inter-agent communication to eliminate false positives:

### Phase 1: Audit Agents (find candidates)
- Launch parallel agents that compare Figma designs against Android code
- Each agent writes findings to `/tmp/figma-audit-findings/group-N.jsonl` as structured JSON
- Agents DO NOT create GitHub issues — only collect evidence

### Phase 2: Review Agents (verify & create issues)
- After Phase 1 completes, launch review agents that cross-verify findings
- Each reviewer independently fetches Figma designs and reads code
- Only findings that pass independent verification become GitHub issues
- False positives are logged and skipped

## Instructions

1. **Parse the input**: Extract fileKey, nodeId from URLs, or map screen names to the Figma Section Map in the `audit-figma` skill

2. **For "all" mode**: Follow the full two-phase workflow in the `audit-figma` skill:
   - Phase 1: 9 parallel audit agents write findings to JSONL files
   - Wait for all to complete
   - Phase 2: Launch review agents to verify and create issues

3. **For single screen**: Run both phases inline (audit + self-verify before creating issues)

4. **Quality gates** (ALL must pass before creating an issue):
   - Figma value independently verified with exact property name
   - Code value independently verified with file path and line number
   - Difference is visually perceptible (>= 2dp/2sp or clearly different color)
   - Correct Figma node compared to correct screen
   - Not iOS-specific, not on an active feature branch, not intentionally disabled

5. **Common false positives to reject**:
   - Same hex color under different semantic tokens
   - iOS system UI elements (home indicator, tab bars)
   - Speculative "needs verification" claims
   - Differences < 2dp/2sp
   - Features on active branches (check `git branch -a`)
   - Platform-appropriate Android adaptations
