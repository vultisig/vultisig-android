package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoPriorityFee
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRelayedIntent
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The gate between what a relayed Kamino transaction says and what the payload around it claims
 * (issue #5644).
 */
internal class KaminoJoinIntentTest {

    private val vault = KaminoVaultRegistry.ALLEZ_SOL

    private val solCoin =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "sol",
            address = SIGNER,
            decimal = 9,
            hexPublicKey = "hex",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `a deposit whose amount and destination agree is kept`() {
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(payload(toAddress = vault.address, toAmount = AMOUNT)) shouldBe
            intent
    }

    @Test
    fun `a deposit stating an amount the transaction does not carry is refused`() {
        // The reported symptom in reverse: the two devices must not describe one transaction with
        // two amounts, so a payload that disagrees with its own bytes loses the deposit framing.
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(
            payload(toAddress = vault.address, toAmount = AMOUNT + BigInteger.ONE)
        ) shouldBe null
    }

    @Test
    fun `a deposit pointed somewhere other than the vault is refused`() {
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(payload(toAddress = SIGNER, toAmount = AMOUNT)) shouldBe null
    }

    @Test
    fun `a withdraw paying the signer is kept, its share count never compared with tokens`() {
        // The instruction carries shares and the payload carries the tokens they are worth, so the
        // two figures legitimately differ.
        val intent = intent(KaminoAction.WITHDRAW, BigInteger.valueOf(500_000))

        intent.takeIfDescribedBy(payload(toAddress = SIGNER, toAmount = AMOUNT)) shouldBe intent
    }

    @Test
    fun `a withdraw pointed at the vault is refused`() {
        // A withdraw pays the signer; naming the vault as the destination tells the approving user
        // the opposite of what the transaction does.
        val intent = intent(KaminoAction.WITHDRAW, BigInteger.valueOf(500_000))

        intent.takeIfDescribedBy(payload(toAddress = vault.address, toAmount = AMOUNT)) shouldBe
            null
    }

    @Test
    fun `an asset that is not the vault's underlying token is refused`() {
        // Base units are the same number whatever scale is claimed for them, so the amount check
        // above passes — and the screen then divides that number by the wrong power of ten. A
        // nine-decimal SOL deposit relayed as six decimals reads a thousand times larger.
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(
            payload(toAddress = vault.address, toAmount = AMOUNT, coin = solCoin.copy(decimal = 6))
        ) shouldBe null
    }

    @Test
    fun `an asset under a false ticker is refused`() {
        // The hero reads "You're depositing <amount> <ticker>": a true figure beside the wrong
        // asset name is still a false claim about what is leaving the wallet.
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(
            payload(
                toAddress = vault.address,
                toAmount = AMOUNT,
                coin = solCoin.copy(ticker = "USDC"),
            )
        ) shouldBe null
    }

    @Test
    fun `a payload quoting a fee its own bytes do not charge is refused`() {
        // The fee row is priced from the recorded budget while the runtime charges the one in the
        // instructions, and the display clamps where the charge does not — so a low recorded price
        // beside a high instruction price is an unbounded fee under a capped figure.
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(
            payload(
                toAddress = vault.address,
                toAmount = AMOUNT,
                recordedPrice = BigInteger.valueOf(20_000),
            )
        ) shouldBe null
    }

    @Test
    fun `a payload recording a limit the bytes do not reserve is refused`() {
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        intent.takeIfDescribedBy(
            payload(
                toAddress = vault.address,
                toAmount = AMOUNT,
                recordedLimit = BigInteger.valueOf(100_000),
            )
        ) shouldBe null
    }

    @Test
    fun `bytes carrying no readable budget are refused whatever the payload records`() {
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT, priorityFee = null)

        intent.takeIfDescribedBy(payload(toAddress = vault.address, toAmount = AMOUNT)) shouldBe
            null
    }

    @Test
    fun `a payload for another chain is refused rather than cast`() {
        // Recognition keys off the coin's chain, which does not settle what shape the specific is —
        // and the fee row and the amount scale both read fields only a Solana one carries.
        val intent = intent(KaminoAction.DEPOSIT, AMOUNT)

        val ethereumSpecific =
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = BigInteger.ONE,
                priorityFeeWei = BigInteger.ONE,
                nonce = BigInteger.ZERO,
                gasLimit = BigInteger.ONE,
            )

        intent.takeIfDescribedBy(
            payload(toAddress = vault.address, toAmount = AMOUNT)
                .copy(blockChainSpecific = ethereumSpecific)
        ) shouldBe null
    }

    private fun intent(
        action: KaminoAction,
        amount: BigInteger,
        priorityFee: KaminoPriorityFee? = RELAYED_BUDGET,
    ) =
        KaminoRelayedIntent(
            vault = vault,
            action = action,
            amount = amount,
            priorityFee = priorityFee,
        )

    private fun payload(
        toAddress: String,
        toAmount: BigInteger,
        coin: Coin = solCoin,
        recordedPrice: BigInteger = RELAYED_BUDGET.price,
        recordedLimit: BigInteger = RELAYED_BUDGET.limit,
    ) =
        KeysignPayload(
            coin = coin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "hash",
                    priorityFee = recordedPrice,
                    priorityLimit = recordedLimit,
                ),
            vaultPublicKeyECDSA = "pubkey",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val SIGNER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val AMOUNT: BigInteger = BigInteger.valueOf(429_219_030)

        /** What the instructions carry, and what the payload has to record to be believed. */
        val RELAYED_BUDGET =
            KaminoPriorityFee(
                limit = BigInteger.valueOf(350_000),
                price = BigInteger.valueOf(250_000),
            )
    }
}
