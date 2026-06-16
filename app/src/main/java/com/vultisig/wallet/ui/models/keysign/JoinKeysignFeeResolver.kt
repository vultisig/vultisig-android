package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.FourByteRepository
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the join-keysign network fee and decodes EVM function info for the verify UI models.
 *
 * Extracted verbatim from `JoinKeysignViewModel` so the per-transaction-type builders can share the
 * exact fee-resolution and EVM-decoding logic the ViewModel used inline. Behavior is unchanged.
 */
internal class JoinKeysignFeeResolver
@Inject
constructor(
    private val fourByteRepository: FourByteRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val parseCosmosMessage: ParseCosmosMessageUseCase,
) {

    /**
     * Decode the function signature and pretty-formatted args from EVM calldata.
     *
     * The function name is split on camelCase boundaries and title-cased so it reads as a label
     * (e.g. `supplyWithPermit` → `"Supply With Permit"`). Caller renders the name as a small-text
     * heading above the resolved-amount Blockaid hero.
     *
     * Args decoding is best-effort. When it fails (malformed ABI, unsupported tuple shape, etc.)
     * the signature + function name still surface so the user sees *what* is being called, just
     * without the pretty-printed inputs row.
     *
     * 4byte HTTP + JNI ABI decode + JSON encode are bounced onto IO so the caller's dispatcher
     * (typically the main / unconfined coroutine that runs `keysignVerify`) is never blocked.
     */
    suspend fun getTransactionFunctionInfo(memo: String?, chain: Chain): FunctionInfo? {
        if (chain.standard != TokenStandard.EVM || memo.isNullOrEmpty()) return null
        return withContext(Dispatchers.IO) {
            val functionSignature =
                fourByteRepository.decodeFunction(memo) ?: return@withContext null
            FunctionInfo(
                signature = functionSignature,
                inputs = fourByteRepository.decodeFunctionArgs(functionSignature, memo),
                functionName = prettifyEvmFunctionName(functionSignature),
            )
        }
    }

    /**
     * Network Fee for the join-keysign deposit and non-UTXO send paths: the dApp-supplied fee when
     * present ([dappSuppliedNativeFee], issue #4390), otherwise [computeJoinKeysignNetworkFee]. The
     * fallback and its fee-service call only run when there is no dApp fee.
     */
    suspend fun resolveJoinKeysignNetworkFee(
        payload: KeysignPayload,
        chain: Chain,
        nativeCoin: Coin,
        blockchainTransaction: Transfer,
    ): TokenValue =
        payload.dappSuppliedNativeFee(chain, parseCosmosMessage)?.let {
            TokenValue(value = it, token = nativeCoin)
        }
            ?: computeJoinKeysignNetworkFee(
                blockChainSpecific = payload.blockChainSpecific,
                nativeCoin = nativeCoin,
                fallbackFeeAmount =
                    resolveFallbackFeeAmount(payload.blockChainSpecific, blockchainTransaction),
            )

    /**
     * Returns ZERO for EVM/THORChain (fee is read from [BlockChainSpecific]) or fetches via the fee
     * service otherwise.
     */
    private suspend fun resolveFallbackFeeAmount(
        blockChainSpecific: BlockChainSpecific,
        blockchainTransaction: Transfer,
    ): BigInteger =
        if (
            blockChainSpecific is BlockChainSpecific.Ethereum ||
                blockChainSpecific is BlockChainSpecific.THORChain
        ) {
            BigInteger.ZERO
        } else {
            withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(blockchainTransaction) }
                .amount
        }
}
