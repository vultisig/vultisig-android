@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.ton.Tonstakers
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.OPERATION_TONSTAKERS_STAKE
import com.vultisig.wallet.data.models.OPERATION_TONSTAKERS_UNSTAKE
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

/** Pool name shown on the verify screen's Pool row for both Tonstakers directions. */
internal const val TONSTAKERS_POOL_NAME = "Tonstakers"

/**
 * Builds the Tonstakers stake [DepositTransaction]: a TON transfer of [depositValue] to the pool
 * carrying the `pool::deposit` body. [depositValue] already includes [Tonstakers.DEPOSIT_FEE] and
 * has cleared the minimum — the form owns that validation, this only assembles what signs.
 *
 * The body rides as a TonConnect-style message ([SignTon]) rather than a text comment, which every
 * co-signer already MPC-signs for dApp requests, so no relay-schema field is added. The destination
 * is the pool's bounceable `EQ…` form so a deposit the pool rejects bounces back instead of being
 * absorbed.
 */
internal suspend fun buildTonstakersStakeTransaction(
    vaultId: String,
    depositValue: BigInteger,
    accountsRepository: AccountsRepository,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    getFeesFiatValue: suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
): DepositTransaction {
    val nativeCoin = nativeTonCoin(vaultId, accountsRepository)
    val srcAddress = nativeCoin.address
    val gasFee = calculateGasFee(Chain.Ton, nativeCoin, srcAddress)
    val specific =
        tonSpecific(
            blockChainSpecificRepository,
            srcAddress,
            nativeCoin,
            gasFee,
            dstAddress = Tonstakers.POOL_ADDRESS,
        )
    val gasFeeFiat = getFeesFiatValue(specific, gasFee, nativeCoin)

    return DepositTransaction(
        id = Uuid.random().toString(),
        vaultId = vaultId,
        srcToken = nativeCoin,
        srcAddress = srcAddress,
        dstAddress = Tonstakers.POOL_ADDRESS,
        memo = "",
        srcTokenValue = TokenValue(value = depositValue, token = nativeCoin),
        estimatedFees = gasFee,
        estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        blockChainSpecific = specific.blockChainSpecific,
        operation = OPERATION_TONSTAKERS_STAKE,
        pool = TONSTAKERS_POOL_NAME,
        signTon =
            SignTon(
                tonMessages =
                    listOf(
                        TonMessage(
                            to = Tonstakers.POOL_ADDRESS,
                            amount = depositValue.toString(),
                            payload = Tonstakers.depositBody(),
                        )
                    )
            ),
    )
}

/**
 * Builds the Tonstakers unstake [DepositTransaction]: a burn of [burnAmount] tsTON base units sent
 * to the vault's own tsTON jetton wallet ([jettonWalletAddress], bounceable form), carrying
 * [Tonstakers.UNSTAKE_ATTACHED_VALUE] so the request still clears the pool's withdrawal fee after
 * two hops. The excesses come back to the vault's address.
 *
 * [tsTonCoin] is what the verify screen shows leaving — the tsTON quantity — while the payload is
 * built from the native coin, which is what signs a message batch and pays its value.
 */
internal suspend fun buildTonstakersUnstakeTransaction(
    vaultId: String,
    burnAmount: BigInteger,
    jettonWalletAddress: String,
    tsTonCoin: Coin,
    accountsRepository: AccountsRepository,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    getFeesFiatValue: suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
): DepositTransaction {
    val nativeCoin = nativeTonCoin(vaultId, accountsRepository)
    val srcAddress = nativeCoin.address
    val body =
        Tonstakers.burnBody(amount = burnAmount, responseAddress = srcAddress)
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
    val gasFee = calculateGasFee(Chain.Ton, nativeCoin, srcAddress)
    val specific =
        tonSpecific(
            blockChainSpecificRepository,
            srcAddress,
            nativeCoin,
            gasFee,
            dstAddress = jettonWalletAddress,
        )
    val gasFeeFiat = getFeesFiatValue(specific, gasFee, nativeCoin)

    return DepositTransaction(
        id = Uuid.random().toString(),
        vaultId = vaultId,
        srcToken = tsTonCoin.copy(address = srcAddress),
        srcAddress = srcAddress,
        dstAddress = jettonWalletAddress,
        memo = "",
        srcTokenValue = TokenValue(value = burnAmount, token = tsTonCoin),
        estimatedFees = gasFee,
        estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        blockChainSpecific = specific.blockChainSpecific,
        operation = OPERATION_TONSTAKERS_UNSTAKE,
        pool = TONSTAKERS_POOL_NAME,
        signTon =
            SignTon(
                tonMessages =
                    listOf(
                        TonMessage(
                            to = jettonWalletAddress,
                            amount = Tonstakers.UNSTAKE_ATTACHED_VALUE.toString(),
                            payload = body,
                        )
                    )
            ),
    )
}

private suspend fun nativeTonCoin(vaultId: String, accountsRepository: AccountsRepository): Coin =
    accountsRepository
        .loadAddress(vaultId, Chain.Ton)
        .first()
        .accounts
        .firstOrNull { it.token.isNativeToken }
        ?.token
        ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.ton_defi_error_ton_not_in_vault)
        )

/**
 * The TON chain-specific block for a message to [dstAddress]. Passing the bounceable destination is
 * what makes `bounceable` resolve true; a co-signer applies that one flag to every message in the
 * batch, so it has to match the `EQ…` tag the destination carries.
 */
private suspend fun tonSpecific(
    blockChainSpecificRepository: BlockChainSpecificRepository,
    srcAddress: String,
    nativeCoin: Coin,
    gasFee: TokenValue,
    dstAddress: String,
): BlockChainSpecificAndUtxo =
    blockChainSpecificRepository.getSpecific(
        Chain.Ton,
        srcAddress,
        nativeCoin,
        gasFee,
        isSwap = false,
        isMaxAmountEnabled = false,
        isDeposit = false,
        dstAddress = dstAddress,
    )
