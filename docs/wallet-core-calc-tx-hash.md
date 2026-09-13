# `TransactionUtil.calcTxHash` returns null for unsupported coins

`wallet.core.jni.TransactionUtil.calcTxHash(coinType, encodedTx)` looks like a drop-in
replacement for the transaction ids we compute locally. It is not one for every chain: the
native returns **null** when the coin has no `TransactionUtil` in Wallet Core's Rust registry,
and also when the input is not in the encoding that coin's implementation decodes. The two
cases are indistinguishable from the caller's side.

## Why it surfaces as an NPE

The JNI binding is declared without a nullability annotation:

```java
public static native String calcTxHash(CoinType coinType, String encodedTx);
```

Kotlin sees a platform type (`String!`). Nothing fails at the call itself — the null becomes
`NullPointerException: calcTxHash(...) must not be null` at the first place the value is used
as non-null, typically when it is returned from a function declared `: String`.

Read it into a declared nullable and handle the null where it is produced:

```kotlin
val hash: String? = TransactionUtil.calcTxHash(coinType, encodedTx)
    ?: return null
```

## Which of our chains it supports (Wallet Core 4.8.0)

Dispatch is `CoinType` → `registry.json` blockchain type → Rust `CoinEntry` → its
`transaction_util()`. An entry that declares `NoTransactionUtil` returns `Error_not_supported`;
a chain still implemented in the C++ tree never reaches an entry at all. Both come back as null.

| Chains | Blockchain type | `calcTxHash` | Input it decodes → result |
|---|---|---|---|
| Bitcoin, Litecoin, Dogecoin, Dash | `Bitcoin` | yes | hex signed tx → txid, RPC byte order |
| Bitcoin Cash | `BitcoinCash` | yes | hex signed tx → txid, RPC byte order |
| Ethereum and every EVM chain | `Ethereum` | yes | hex signed tx → `0x` keccak256 |
| Solana | `Solana` | yes | base64 signed tx → first signature |
| Sui | `Sui` | yes | base64 `TransactionData` → blake2b digest |
| TON | `TheOpenNetwork` | yes | base64 BoC → root-cell hash (the message hash, not the on-chain tx hash) |
| Polkadot, Bittensor | `Polkadot` | yes | hex extrinsic → `0x` blake2b-256 |
| Cosmos, dYdX, Osmosis, Terra, Terra Classic, Noble, Akash | `Cosmos` | yes | base64 `tx_bytes` → uppercase SHA-256 |
| THORChain, MayaChain | `Thorchain` | **null** | entry declares `NoTransactionUtil` |
| Ripple | `Ripple` | **null** | entry declares `NoTransactionUtil` |
| Zcash | `Zcash` | **null** | entry declares `NoTransactionUtil` |
| Cardano | — | **null** | no Rust entry (C++ implementation) |
| Tron | — | **null** | no Rust entry (C++ implementation) |

Derived from `rust/tw_coin_registry/src/dispatcher.rs` and each
`rust/chains/*/src/entry.rs` at the `4.8.0` tag; Cardano is also measured on device.

## Where we hash locally, and why that stays

- `data/.../crypto/CardanoUtils.kt` `calculateCardanoTransactionHash` — blake2b-256 over the
  CBOR body. Cardano is unsupported above, and the function fails closed to `""` on a parse
  error on purpose: a hash over the wrong bytes would be a txid that never exists on-chain, and
  duplicate-broadcast recovery would report it as an untrackable success.
- `data/.../models/CosmoSignature.kt` `transactionHash()` — SHA-256 over the broadcast bytes.
  Identical to what Wallet Core computes for the `Cosmos` type, but the same function serves the
  THORChain and MayaChain helpers, for which `calcTxHash` returns null.

## Re-checking after a Wallet Core bump

`data/src/androidTest/.../crypto/WalletCoreCalcTxHashTest.kt` pins the three rows above that
matter to our code (Cardano null, THORChain null, Cosmos equal to the local digest) and runs in
CI's `data:connectedDebugAndroidTest`. If a bump flips a null row, the matching local hash is a
candidate for removal. For any other coin, check `type TransactionUtil` in its `entry.rs` at the
new tag, then confirm on a device or emulator — the natives do not load on the host JVM.
