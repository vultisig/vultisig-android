package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoDepositRentReserve
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRelayedIntent
import com.vultisig.wallet.data.blockchain.solana.kamino.kaminoNetworkFeeLamports
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_KAMINO_DEPOSIT
import com.vultisig.wallet.data.models.OPERATION_KAMINO_WITHDRAW
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ThorchainMemoParser
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the [VerifyUiModel.Deposit] model for the join-keysign verify screen. Extracted verbatim
 * from `JoinKeysignViewModel.loadTransaction`'s deposit branch — behavior is unchanged.
 */
internal class JoinDepositUiModelBuilder
@Inject
constructor(
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapDepositTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val mapDepositTransactionHistoryData: DepositTransactionHistoryDataMapper,
    private val thorchainMemoParser: ThorchainMemoParser,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val feeResolver: JoinKeysignFeeResolver,
    private val depositRentReserve: KaminoDepositRentReserve,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
) {

    /**
     * Builds the deposit [JoinKeysignVerifyResult] from [payload] for the vault identified by
     * [vaultId], resolving fees and parsing the THOR memo.
     *
     * @param kamino what the relayed Solana bytes turned out to be, when they were recognised as a
     *   Kamino Earn deposit or withdraw. It carries everything a THOR memo carries for the chains
     *   above — which vault, which direction — none of which a Kamino payload states in a field
     *   (issue #5644).
     */
    suspend fun build(
        payload: KeysignPayload,
        vaultId: String,
        kamino: KaminoRelayedIntent? = null,
    ): JoinKeysignVerifyResult {
        when (payload.blockChainSpecific) {
            is BlockChainSpecific.MayaChain,
            is BlockChainSpecific.THORChain,
            is BlockChainSpecific.Ethereum,
            is BlockChainSpecific.Cosmos,
            // TON nominator-pool stake/unstake builds a deposit; the joining co-signer must be able
            // to render its verify screen so multi-device staking ceremonies aren't blocked.
            is BlockChainSpecific.Ton,
            is BlockChainSpecific.UTXO -> Unit

            // Solana arrives here only as a recognised Kamino transaction. A raw Solana payload
            // nobody could read is still a send, and rendering it as a deposit would put a
            // deposit's framing around bytes this device cannot describe.
            is BlockChainSpecific.Solana ->
                requireNotNull(kamino) {
                    "a Solana deposit must be a recognised Kamino transaction"
                }

            else -> error("BlockChainSpecific ${payload.blockChainSpecific} is not supported")
        }

        val payloadToken = payload.coin
        val chain = payloadToken.chain

        val tokenValue =
            TokenValue(
                value = payload.toAmount,
                unit = payloadToken.ticker,
                decimals = payloadToken.decimal,
            )

        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")

        val blockchainTransaction =
            Transfer(
                coin = payloadToken,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = tokenValue.value,
                to = payload.toAddress,
                memo = payload.memo,
                isMax = false,
            )

        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
        val estimatedTokenFees =
            kamino?.let { kaminoNetworkFee(it, payload, nativeCoin) }
                ?: feeResolver.resolveJoinKeysignNetworkFee(
                    payload = payload,
                    chain = chain,
                    nativeCoin = nativeCoin,
                    blockchainTransaction = blockchainTransaction,
                )

        val totalGasAndFee =
            gasFeeToEstimatedFee(
                GasFeeParams(
                    gasLimit = BigInteger.valueOf(1),
                    gasFee = estimatedTokenFees,
                    selectedToken = payload.coin,
                )
            )

        val parsedThorMemo = thorchainMemoParser.parse(payload.memo ?: "")

        val depositTransaction =
            DepositTransaction(
                id = UUID.randomUUID().toString(),
                vaultId = vaultId,
                srcToken = payload.coin,
                srcAddress = payload.coin.address,
                dstAddress = payload.toAddress,
                memo = payload.memo ?: "",
                srcTokenValue = tokenValue,
                estimatedFees = estimatedTokenFees,
                estimateFeesFiat = totalGasAndFee.formattedFiatValue,
                blockChainSpecific = payload.blockChainSpecific,
                operation = kamino?.let(::kaminoOperation) ?: parsedThorMemo?.operation.orEmpty(),
                nodeAddress = parsedThorMemo?.nodeAddress.orEmpty(),
                pairedAddress = parsedThorMemo?.pairedAddress.orEmpty(),
                thorAddress = parsedThorMemo?.thorAddress.orEmpty(),
                pool = parsedThorMemo?.pool.orEmpty(),
                // The verify screen labels this row as the vault for a Kamino operation, so the
                // co-signer reads the destination by name rather than as a bare program address.
                validatorName = kamino?.vault?.fallbackName,
                // Renders the instruction breakdown the initiating device shows, which for a
                // pre-built transaction is the only place its real work is visible.
                signSolana = payload.signSolana,
            )
        // Resolve the same From/To labels the initiator renders (issue #5301), so the joining
        // co-signer sees "VaultName (address)" instead of raw thor1… addresses. Mirrors
        // JoinSendUiModelBuilder's Send-side label resolution.
        val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
        val srcVaultName = vault.name
        val dstVaultName =
            payload.toAddress
                .takeIf { it.isNotEmpty() }
                ?.let {
                    resolveDstVaultName(
                        allVaults = allVaults,
                        chain = chain,
                        dstAddress = it,
                        chainAccountAddressRepository = chainAccountAddressRepository,
                    )
                }
        val dstAddressBookTitle =
            if (dstVaultName == null && payload.toAddress.isNotEmpty()) {
                addressBookRepository.getEntry(chain.id, payload.toAddress)?.title
            } else null

        val depositTransactionUiModel =
            mapDepositTransactionToUiModel(depositTransaction)
                .copy(
                    srcVaultName = srcVaultName,
                    dstVaultName = dstVaultName,
                    dstAddressBookTitle = dstAddressBookTitle,
                    unverifiedWithdrawShares = kamino?.let(::withdrawShares),
                )
        return JoinKeysignVerifyResult(
            verifyUiModel = VerifyUiModel.Deposit(VerifyDepositUiModel(depositTransactionUiModel)),
            transactionTypeUiModel = TransactionTypeUiModel.Deposit(depositTransactionUiModel),
            transactionHistoryData = mapDepositTransactionHistoryData(depositTransactionUiModel),
        )
    }

    /**
     * The share figure a withdraw's kVault instruction carries, formatted, or null for a deposit —
     * whose headline amount this device already checked against the same instruction.
     *
     * Sized by the vault's own share scale, which is not the token's: Allez SOL's shares carry six
     * decimals against the token's nine.
     */
    private fun withdrawShares(kamino: KaminoRelayedIntent): String? =
        kamino
            .takeIf { it.action == KaminoAction.WITHDRAW }
            ?.let {
                mapTokenValueToDecimalUiString(
                    TokenValue(value = it.amount, unit = "", decimals = it.vault.sharesDecimals)
                )
            }

    private fun kaminoOperation(kamino: KaminoRelayedIntent): String =
        when (kamino.action) {
            KaminoAction.DEPOSIT -> OPERATION_KAMINO_DEPOSIT
            KaminoAction.WITHDRAW -> OPERATION_KAMINO_WITHDRAW
        }

    /**
     * The fee the initiating device quotes for these bytes, derived here from the relayed compute
     * budget rather than re-estimated.
     *
     * A fee service can only price the transfer it is handed, and this is not one: it is a
     * pre-built transaction carrying its own compute-unit limit — 320,000 to 400,000 against a
     * transfer's 100,000 — plus, on a first native-SOL deposit, the rent for the accounts it
     * creates. Estimating it as a transfer is how the two devices came to quote fees a hundredfold
     * apart for one transaction (issue #5644).
     */
    private suspend fun kaminoNetworkFee(
        kamino: KaminoRelayedIntent,
        payload: KeysignPayload,
        nativeCoin: Coin,
    ): TokenValue {
        val rentReserve =
            if (kamino.action == KaminoAction.DEPOSIT) {
                withContext(Dispatchers.IO) {
                    depositRentReserve(kamino.vault, payload.coin.address)
                }
            } else {
                BigInteger.ZERO
            }
        return TokenValue(
            value =
                kaminoNetworkFeeLamports(
                    vault = kamino.vault,
                    action = kamino.action,
                    // From the instructions, not from the field relayed beside them. The two are
                    // pinned equal by KaminoRelayedIntent.takeIfDescribedBy before this runs, and
                    // the one inside the bytes is the one the runtime charges.
                    relayedUnitPrice = kamino.priorityFee?.price,
                    rentReserve = rentReserve,
                ),
            token = nativeCoin,
        )
    }
}
