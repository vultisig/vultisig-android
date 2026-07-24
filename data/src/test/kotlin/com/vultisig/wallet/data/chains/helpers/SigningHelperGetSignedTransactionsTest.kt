package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigInteger
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSolana

class SigningHelperGetSignedTransactionsTest {

    private val vault = Vault(id = "v1", name = "Test Vault")

    private fun solanaPayload(signSolana: SignSolana) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Solana,
                    ticker = "SOL",
                    logo = "",
                    address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
                    decimal = 9,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = "",
                    priorityFee = BigInteger.ZERO,
                    priorityLimit = BigInteger.ZERO,
                    fromAddressPubKey = null,
                    toAddressPubKey = null,
                    programId = false,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
            signSolana = signSolana,
        )

    // The empty-batch guard lives only in SolanaHelper.getSignedTransactions, so hitting it here
    // proves the real dispatcher took the batch path, not the singular fallback.
    @Test
    fun `signSolana payload without a swap payload routes through the batch path`() {
        val payload = solanaPayload(SignSolana(rawTransactions = emptyList()))

        shouldThrow<IllegalArgumentException> {
            SigningHelper.getSignedTransactions(payload, vault, emptyMap(), BigInteger.ZERO)
        }
    }
}
