#!/usr/bin/env bash
# PreToolUse hook: Block dangerous operations in Vultisig repos.

INPUT="${1:-}"

# ── Mainnet RPC endpoints ──────────────────────────────────────────────────
MAINNET_PATTERNS=(
  "mainnet.infura.io"
  "eth-mainnet.g.alchemy.com"
  "rpc.ankr.com/eth"
  "api.etherscan.io"
  "thornode.ninerealms.com"
  "rpc.cosmos.network"
  "api.solana.com"
  "mainnet-beta"
)

for pattern in "${MAINNET_PATTERNS[@]}"; do
  if echo "$INPUT" | grep -qi "$pattern"; then
    echo "BLOCKED: Mainnet interaction detected (${pattern})."
    echo "Agents must not interact with mainnet RPCs or contracts."
    exit 2
  fi
done

# ── Direct push to protected branches ─────────────────────────────────────
if echo "$INPUT" | grep -qiE "git push.*(origin|upstream)?\s*(main|master)\b"; then
  echo "BLOCKED: Direct push to main/master. Agents must work on branches and open PRs."
  exit 2
fi

# ── PR merge (agents must never merge) ────────────────────────────────────
if echo "$INPUT" | grep -qiE "gh pr merge|git merge (main|master)"; then
  echo "BLOCKED: Agents must not merge PRs. Label 'agent:review' and let a human merge."
  exit 2
fi

# ── Destructive git operations ─────────────────────────────────────────────
DESTRUCTIVE_PATTERNS=(
  "git push.*--force[^-]"
  "git push.*-f "
  "--no-verify"
  "git reset --hard"
  "git checkout \."
  "git checkout -- \."
  "git clean -f"
  "git clean -fd"
  "git branch -D"
  "rm -rf"
)

for pattern in "${DESTRUCTIVE_PATTERNS[@]}"; do
  if echo "$INPUT" | grep -qiE "$pattern"; then
    echo "BLOCKED: Destructive operation detected (${pattern})."
    exit 2
  fi
done

# ── Secret/credential file edits ──────────────────────────────────────────
SECRET_PATTERNS=(
  "\.env$"
  "\.env\."
  "/secret"
  "/credential"
  "\.pem$"
  "\.key$"
  "\.p12$"
  "\.pfx$"
  "\.keystore$"
  "\.jks$"
)

for pattern in "${SECRET_PATTERNS[@]}"; do
  if echo "$INPUT" | grep -qiE "$pattern"; then
    echo "BLOCKED: Edit to sensitive file matching '${pattern}'."
    echo "Agents must not modify secret/credential files."
    exit 2
  fi
done

exit 0
