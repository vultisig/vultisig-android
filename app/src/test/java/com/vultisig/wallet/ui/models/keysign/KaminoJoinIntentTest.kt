package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
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

    private fun intent(action: KaminoAction, amount: BigInteger) =
        KaminoRelayedIntent(vault = vault, action = action, amount = amount)

    private fun payload(toAddress: String, toAmount: BigInteger) =
        KeysignPayload(
            coin = solCoin,
            toAddress = toAddress,
            toAmount = toAmount,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "hash",
                    priorityFee = BigInteger.valueOf(250_000),
                    priorityLimit = BigInteger.valueOf(350_000),
                ),
            vaultPublicKeyECDSA = "pubkey",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val SIGNER = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val AMOUNT: BigInteger = BigInteger.valueOf(429_219_030)
    }
}
