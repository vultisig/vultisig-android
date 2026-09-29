package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * A SwapKit EVM route approves a token-transfer proxy (`allowanceTarget`) rather than the swap's
 * `to`. The keysign proto doesn't carry that field (vultisig/commondata#116), so a co-signer sees
 * it as null and must fail closed instead of signing an approve to an unverified spender.
 */
class SigningHelperApproveBoundToSwapTest {

    @Test
    fun `SwapKit approve to the allowanceTarget proxy is signed when the proxy is known`() {
        SigningHelper.requireApproveBoundToSwap(
            approve(spender = PROXY),
            payload(provider = "swapkit", allowanceTarget = PROXY),
        )
    }

    @Test
    fun `SwapKit approve to another spender is rejected when the proxy is known`() {
        shouldThrow<IllegalArgumentException> {
            SigningHelper.requireApproveBoundToSwap(
                approve(spender = ATTACKER),
                payload(provider = "swapkit", allowanceTarget = PROXY),
            )
        }
    }

    // What a co-signer reconstructs from the proto today: the proxy is dropped, so it can't be
    // told apart from any other address.
    @Test
    fun `SwapKit approve to a proxy is rejected when the payload carries no allowanceTarget`() {
        val error =
            shouldThrow<IllegalArgumentException> {
                SigningHelper.requireApproveBoundToSwap(
                    approve(spender = ATTACKER),
                    payload(provider = "swapkit", allowanceTarget = null),
                )
            }

        error.message shouldContain "allowanceTarget"
    }

    @Test
    fun `SwapKit approve to the swap router is signed when the payload carries no allowanceTarget`() {
        SigningHelper.requireApproveBoundToSwap(
            approve(spender = ROUTER),
            payload(provider = "swapkit", allowanceTarget = null),
        )
    }

    @Test
    fun `SwapKit approve above the swap amount is rejected`() {
        shouldThrow<IllegalArgumentException> {
            SigningHelper.requireApproveBoundToSwap(
                approve(spender = PROXY, amount = AMOUNT + BigInteger.ONE),
                payload(provider = "swapkit", allowanceTarget = PROXY),
            )
        }
    }

    @Test
    fun `1inch approve falls back to the swap router as spender`() {
        SigningHelper.requireApproveBoundToSwap(
            approve(spender = ROUTER),
            payload(provider = "1inch", allowanceTarget = null),
        )
        shouldThrow<IllegalArgumentException> {
            SigningHelper.requireApproveBoundToSwap(
                approve(spender = ATTACKER),
                payload(provider = "1inch", allowanceTarget = null),
            )
        }
    }

    private fun approve(spender: String, amount: BigInteger = AMOUNT) =
        ERC20ApprovePayload(amount = amount, spender = spender)

    private fun payload(provider: String, allowanceTarget: String?) =
        KeysignPayload(
            coin = usdt,
            toAddress = ROUTER,
            toAmount = AMOUNT,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger("1000000000"),
                    priorityFeeWei = BigInteger("100000000"),
                    nonce = BigInteger.valueOf(7),
                    gasLimit = BigInteger.valueOf(210_000),
                ),
            swapPayload =
                SwapPayload.EVM(
                    EVMSwapPayloadJson(
                        fromCoin = usdt,
                        toCoin = usdt.copy(ticker = "ETH", contractAddress = "", isNativeToken = true),
                        fromAmount = AMOUNT,
                        toAmountDecimal = BigDecimal.ONE,
                        quote =
                            EVMSwapQuoteJson(
                                dstAmount = "1",
                                tx =
                                    OneInchSwapTxJson(
                                        from = usdt.address,
                                        to = ROUTER,
                                        allowanceTarget = allowanceTarget,
                                        gas = 210_000,
                                        data = "0x",
                                        value = "0",
                                        gasPrice = "1000000000",
                                    ),
                            ),
                        provider = provider,
                    )
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private val usdt =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDT",
            logo = "usdt",
            address = "0x1234567890123456789012345678901234567890",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            isNativeToken = false,
        )

    private companion object {
        const val ROUTER = "0x1111111111111111111111111111111111111111"
        const val PROXY = "0x6c0ad82f9721a6dc986381d19338601a2e6370e5"
        const val ATTACKER = "0xbadbadbadbadbadbadbadbadbadbadbadbadbad0"
        val AMOUNT: BigInteger = BigInteger.valueOf(5_000_000)
    }
}
