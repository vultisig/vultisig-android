# SwapKit Swap-Fee Integration — iOS ↔ Android Parity

Reference for how the SwapKit **inbound (swap) fee** is computed, scaled, and surfaced on each
platform, and where Android Phase 1 (PR #4619, EVM + Solana) intentionally diverges from iOS. Use
this when building out Android Phase 2 (non-EVM chains) so the fee path lands correctly the first
time.

> The fee here is the SwapKit **inbound** fee — the source-chain cost SwapKit attributes to a route
> (`route.fees[]` with `type == "inbound"`). It is the value shown as the user-facing **Network
> Fee** on the verify screen. On both platforms it is **display + balance-check only**; it does NOT
> modify the signed transaction, because SwapKit pre-builds the tx with the fee already baked in.

## What's implemented

| Route family | iOS | Android (Phase 1) |
|---|---|---|
| Solana (native SOL + SPL tokens) | ✅ | ✅ |
| EVM | ✅ | ✅ |
| Non-EVM (BTC, TRON, TON, SUI, ADA, XRP, ZEC, DOGE/BCH/DASH) | ✅ per-chain signers | ❌ `SwapKitError.UnsupportedTxType` — deferred to Phase 2 |

Android refuses non-EVM SwapKit routes today: `txTypeOf` maps anything that isn't `evm` /
`solana` / `serialized_base64` to `TxKind.UNSUPPORTED`, which throws `UnsupportedTxType`
(`data/.../swap/SwapKitQuoteSource.kt`). So for non-EVM there is currently **nothing to compare on
the fee side** — only Solana is a true apples-to-apples comparison.

## Fee computation & scaling (the crux)

Both platforms read the same wire field — the `route.fees[]` entry with `type == "inbound"` matched
on the source-chain prefix — and the amount is a decimal string denominated in the **source chain's
native gas coin** (e.g. `ETH.ETH`, `SOL.SOL`), never the sell token. They differ on how they scale
it to base units:

| | Scaling basis | Native source (e.g. SOL→x) | Token source (e.g. USDC→x) |
|---|---|---|---|
| **iOS** — `SwapKitService.inboundFee` → `Coin.raw(for:)` (`× 10^fromCoin.decimals`) | the **sell token's** decimals | ✅ correct (9) | ⚠️ **under-counts** — scales a native fee by the token's decimals (e.g. 6), off by 10^(native−token) |
| **Android** — `inboundFeeRawUnits` → `× 10^nativeDecimals(srcToken.chain)` (SOL = 9, EVM = 18) | the source chain's **native** decimals | ✅ correct (9) | ✅ correct (9) |

`SwapKitQuoteSource.inboundFeeRawUnits` / `nativeDecimals` carry the Android behavior.

iOS's `inboundFee` tests (`SwapKitServiceInboundFeeTests`) only cover **native** sources
(SOL / TRX / ETH-native), so the token-source under-count is currently untested there. Android pins
the token-source case (USDC source, native-ETH fee → `11658769527210`, not the truncated `11`) in
`SwapKitQuoteSourceTest`.

**Takeaway:** Android's Solana fee scaling is more correct than iOS for SPL-token sources. When
Android builds non-EVM, keep `nativeDecimals(chain)` — do not copy iOS's `fromCoin.decimals`.

## How the fee is surfaced (plumbing differs, behavior agrees)

Both treat the fee as display + balance-check only. The plumbing differs:

- **iOS** — first-class field: `SwapQuote.swapkit(response, fee: BigInt?, subProvider:)`. Consumed
  by `SwapCryptoLogic.fee()` → Network-Fee display + `balanceError()` gas check.
- **Android** — smuggled through the EVM envelope: the raw fee goes into
  `OneInchSwapTxJson.swapFee` with `swapFeeTokenContract = ""` (native-coin sentinel), then
  `SwapQuoteManager.resolveSwapFee` resolves the empty contract back to `srcNativeToken`. This is
  because Phase 1 reuses `EVMSwapQuoteJson` for Solana rather than a dedicated SwapKit quote type.

## Non-EVM specifics (iOS only)

iOS signers consume SwapKit's pre-built, fee-inclusive tx verbatim — the fee is baked into the
route payload, and the inbound fee is display/balance only:

- **BTC / ZEC / DOGE / BCH / DASH** (PSBT), **SUI** (PTB), **ADA** (CBOR), **TRON** (`raw_data`
  `fee_limit`): fee is in the route tx; signer doesn't touch it.
- **TON & XRP** are outliers — deposit-only / standard-transfer flows where the **signer** sets the
  fee via `chainSpecific.gas`, *not* the SwapKit inbound fee.

## Phase 2 guidance for Android

1. Keep native-decimal fee scaling (`nativeDecimals(chain)`); extend the map as non-EVM chains land.
2. The `OneInchSwapTxJson.swapFee` smuggle will not extend cleanly to PSBT / CBOR / PTB routes —
   add a first-class fee field on the SwapKit quote type (mirror iOS' `SwapQuote.swapkit(fee:)`)
   instead of reusing the EVM envelope.
3. For TON / XRP, expect a signer-set gas fee rather than the inbound fee (iOS parity).
4. Treat the fee as display + balance-check only — SwapKit's pre-built tx already includes it.

## Cross-platform follow-up

iOS has a latent under-count for ERC-20 / SPL-token sources (`inboundFee` scales by the sell
token's decimals). Track on the iOS side; Android already scales correctly.
