@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.gov.CosmosGovVote
import com.vultisig.wallet.data.blockchain.cosmos.gov.CosmosGovVoteSignDataResolver
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.cosmosNativeDenom
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builds a governance vote: one `MsgVote` signed as `signDirect` bytes, the format the extension
 * and desktop send, so every co-signer signs it without vote-specific code.
 *
 * The payload moves nothing (`toAmount` 0), carries no memo, and names the voter as its `toAddress`
 * — never signed for a `signDirect` tx, but iOS `DydxHelperStruct` refuses a payload whose
 * `toAddress` is not a valid dYdX address.
 */
internal class GovVoteStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val selectedTokenProvider: () -> Coin?,
    private val proposalIdFieldState: TextFieldState,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
    private val cosmosChainId: (Chain) -> String = { it.coinType.chainId() },
) : DepositSubmitStrategy {

    override suspend fun build(): DepositTransaction {
        val vaultId =
            requireNotNull(vaultIdProvider()) {
                "vaultId must be initialized before creating transaction"
            }
        val chain =
            chainProvider()
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )
        val option =
            stateProvider().selectedVoteOption
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_form_vote_option_error)
                )
        val proposalId =
            CosmosGovVote.parseProposalId(proposalIdFieldState.text.toString())
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_form_proposal_id_error)
                )
        val vote = CosmosGovVote(proposalId = proposalId, option = option)

        val voter =
            selectedTokenProvider()?.takeIf { it.isNativeToken && it.chain == chain }
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )
        val voterAddress = voter.address

        val gasFee = calculateGasFee(chain, voter, voterAddress)

        val specific =
            blockChainSpecificRepository.getSpecific(
                chain = chain,
                address = voterAddress,
                token = voter,
                gasFee = gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )
        val cosmosSpecific =
            specific.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Governance vote requires Cosmos chain data, got ${chain.raw}")

        val signDirect =
            CosmosGovVoteSignDataResolver.resolve(
                vote = vote,
                voterAddress = voterAddress,
                hexPublicKey = voter.hexPublicKey,
                chainId = cosmosChainId(chain),
                chainSpecific = cosmosSpecific,
                feeDenom =
                    requireNotNull(chain.cosmosNativeDenom) { "No fee denom for ${chain.raw}" },
                staticGasLimit = CosmosHelper.getChainGasLimit(chain),
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, voter)

        return DepositTransaction(
            id = Uuid.random().toString(),
            vaultId = vaultId,
            srcToken = voter,
            srcAddress = voterAddress,
            dstAddress = voterAddress,
            memo = "",
            srcTokenValue = TokenValue(value = BigInteger.ZERO, token = voter),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = cosmosSpecific,
            signDirect = signDirect,
            governanceVote = vote,
        )
    }
}
