package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import java.math.BigInteger
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignBitcoin
import vultisig.keysign.v1.SignRipple
import vultisig.keysign.v1.SignSolana
import vultisig.keysign.v1.SignSui
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TronTransferAssetContractPayload
import vultisig.keysign.v1.TronTransferContractPayload
import vultisig.keysign.v1.TronTriggerSmartContractPayload
import vultisig.keysign.v1.WasmExecuteContractPayload

data class KeysignPayload(
    val coin: Coin,
    val toAddress: String,
    val toAmount: BigInteger,
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
    val memo: String? = null,
    val swapPayload: SwapPayload? = null,
    val approvePayload: ERC20ApprovePayload? = null,
    val vaultPublicKeyECDSA: String,
    val vaultLocalPartyID: String,
    val libType: SigningLibType?,
    val wasmExecuteContractPayload: WasmExecuteContractPayload?,
    val signAmino: SignAmino? = null,
    val signDirect: SignDirectProto? = null,
    val signSolana: SignSolana? = null,
    val signTon: SignTon? = null,
    /**
     * Pre-built Sui Programmable Transaction Block (PTB) supplied by an external dApp via the Sui
     * Wallet Standard. Carries the base64-encoded `TransactionData` BCS bytes to sign verbatim;
     * when present, [SuiHelper] signs through WalletCore's `SignDirect` path instead of rebuilding
     * a `Pay`/`PaySui`, and the Sui [blockChainSpecific] slot is an empty placeholder (the coins,
     * gas budget and recipients are already baked into the bytes).
     */
    val signSui: SignSui? = null,
    /**
     * Pre-built XRPL transaction supplied by an external dApp (via the extension's GemWallet
     * provider) — an `OfferCreate`, cross-currency `Payment`, `TrustSet`, etc. Carries the raw
     * transaction JSON to sign verbatim; when present, [RippleHelper] builds its signing input via
     * WalletCore's `rawJson` path instead of reconstructing an `OperationPayment` from
     * `toAddress`/`toAmount`, so every co-signer produces byte-identical bytes (matching the
     * extension and `@vultisig/core-mpc`). The Ripple [blockChainSpecific] slot is an empty
     * placeholder — sequence, fee and last-ledger-sequence are already baked into the JSON.
     */
    val signRipple: SignRipple? = null,
    /**
     * Structured Bitcoin PSBT payload supplied by an external dApp for co-signing. When present,
     * the UTXO signing path uses these inputs/outputs directly and bypasses WalletCore tx planning;
     * `utxos` and the UTXO `blockChainSpecific` slot are unused.
     */
    val signBitcoin: SignBitcoin? = null,
    val tronTransferContractPayload: TronTransferContractPayload? = null,
    val tronTriggerSmartContractPayload: TronTriggerSmartContractPayload? = null,
    val tronTransferAssetContractPayload: TronTransferAssetContractPayload? = null,
    val skipBroadcast: Boolean = false,
    /** Marks a QBTC claim BTC-ownership keysign so a peer routes to the claim co-sign path. */
    val isQbtcClaim: Boolean = false,
    val defiAction: DeFiAction = DeFiAction.NONE,
    /**
     * Identity of the dApp that originated this keysign request (name, URL, icon), if the request
     * came from an external dApp via the extension. Informational only — Blockaid + decoded
     * calldata remain the security source of truth.
     */
    val dappMetadata: DAppMetadata? = null,
)

enum class DeFiAction {
    NONE,
    CIRCLE_USDC_WITHDRAW,
}

/**
 * True when the payload carries a pre-encoded Cosmos transaction supplied by an external dApp —
 * either a protobuf SignDoc ([signDirect]) or legacy amino JSON ([signAmino]). Keplr-style dApps
 * (e.g. cosmosrescue.com Terra delegations) sign LUNC via amino, LUNA via either.
 *
 * Such payloads must be signed verbatim by CosmosHelper, which understands both modes. Chain
 * helpers like TerraHelper only know native bank/CW20 sends and would rebuild the message as a
 * `MsgSend` to the `terravaloper…` validator — the chain rejects that with "invalid to address: hrp
 * does not match bech32 prefix".
 */
internal val KeysignPayload.carriesDappCosmosTx: Boolean
    get() = signDirect != null || signAmino != null
