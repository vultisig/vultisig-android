# Issue Writing Guide

_How to fill the Vultisig issue template so agents AND humans produce great results._

---

## Quick Start

1. Pick a template (Bug Report or Feature Request)
2. Fill frontmatter (metadata)
3. Fill body (spec)
4. Run the checklist at the bottom
5. Submit

**Time to fill:** 5-10 minutes for a well-scoped issue. If it takes longer, your scope is too big — split it.

---

## Frontmatter Reference

| Field | Required | Values | Notes |
|-------|----------|--------|-------|
| `type` | Yes | `feature` `bugfix` `refactor` `chore` | Pick one. If it's both a fix and feature, split into two issues |
| `priority` | Yes | `critical` `high` `medium` `low` | Critical = blocks release. High = this sprint. Medium = next sprint. Low = backlog |
| `size` | Yes | `tiny` `small` `medium` | **No "large".** If it's large, decompose into smaller issues |
| `platform` | Yes | `ios` `android` `web` `desktop` `sdk` `server` `docs` | Can be multiple |
| `files.read` | Yes | File paths | Files the implementer needs to read for context |
| `files.write` | Yes | File paths | Files to create or modify. Be specific |
| `verify` | Yes | Shell commands | Commands that prove the work is done |

### Size Guide

| Size | Files Changed | Lines of Code | Example |
|------|--------------|---------------|---------|
| **tiny** | 1 file | <50 lines | Fix a typo, update a constant |
| **small** | 1-3 files | 50-200 lines | Add a function, new component, fix a bug |
| **medium** | 3-8 files | 200-500 lines | New feature with tests, refactor a module |
| **large** | 8+ files | 500+ lines | **SPLIT THIS.** Agent will run out of context |

---

## Body Sections

### Title: `[VERB] [WHAT] [WHERE]`

Start with an action verb. Be specific about location.

| Good | Bad |
|------|-----|
| Fix crash on nil vault in SwapViewModel | Fix bug |
| Add biometric prompt to send flow | Add feature |

### Problem (WHY)

2-3 sentences: What's broken? Who's affected? What happens if we don't fix this?

### Solution (WHAT, not HOW)

1 paragraph describing the approach. Focus on the decision, not implementation details.

### Scope: Must NOT Do

**This section prevents 50% of agent failures.** Always include at least 2 anti-goals:
- "Don't refactor existing code outside the specified files"
- "Don't change string resources without updating all 9 locale files"
- "Don't modify JNI/crypto bindings"

### Acceptance Criteria

| Verifiable | Vague |
|------------|-------|
| `./gradlew assembleDebug` succeeds | Code compiles |
| `./gradlew test` passes | Tests pass |

---

## Pre-Submit Checklist

- [ ] Title starts with a verb
- [ ] Size is tiny/small/medium (never large)
- [ ] files.read has at least 1 path
- [ ] files.write has at least 1 path
- [ ] At least 2 anti-goals in Must NOT Do
- [ ] Every acceptance criterion is command-runnable
- [ ] verify has at least 1 command
