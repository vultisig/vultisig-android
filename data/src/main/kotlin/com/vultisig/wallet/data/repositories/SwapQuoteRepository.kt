package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.swap.JupiterQuoteRequest
import com.vultisig.wallet.data.repositories.swap.JupiterQuoteSource
import com.vultisig.wallet.data.repositories.swap.KyberQuoteRequest
import com.vultisig.wallet.data.repositories.swap.KyberQuoteSource
import com.vultisig.wallet.data.repositories.swap.LiFiQuoteRequest
import com.vultisig.wallet.data.repositories.swap.LiFiQuoteSource
import com.vultisig.wallet.data.repositories.swap.MayaQuoteRequest
import com.vultisig.wallet.data.repositories.swap.MayaQuoteSource
import com.vultisig.wallet.data.repositories.swap.OneInchQuoteRequest
import com.vultisig.wallet.data.repositories.swap.OneInchQuoteSource
import com.vultisig.wallet.data.repositories.swap.ThorChainQuoteRequest
import com.vultisig.wallet.data.repositories.swap.ThorChainQuoteSource
import javax.inject.Inject

interface SwapQuoteRepository {

    suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        referralCode: String = "",
        bpsDiscount: Int = 0,
    ): SwapQuote

    suspend fun getKyberSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        affiliateBps: Int,
    ): EVMSwapQuoteJson

    suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int = 0,
    ): EVMSwapQuoteJson

    suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int = 0,
        referralCode: String = "",
    ): SwapQuote

    suspend fun getLiFiSwapQuote(
        srcAddress: String,
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson

    suspend fun getJupiterSwapQuote(
        srcAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): EVMSwapQuoteJson

    fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider?
}

internal class SwapQuoteRepositoryImpl
@Inject
constructor(
    private val thorChain: ThorChainQuoteSource,
    private val maya: MayaQuoteSource,
    private val oneInch: OneInchQuoteSource,
    private val liFi: LiFiQuoteSource,
    private val jupiter: JupiterQuoteSource,
    private val kyber: KyberQuoteSource,
) : SwapQuoteRepository {

    override suspend fun getSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        referralCode: String,
        bpsDiscount: Int,
    ): SwapQuote =
        thorChain.fetch(
            ThorChainQuoteRequest(
                dstAddress = dstAddress,
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
                referralCode = referralCode,
                bpsDiscount = bpsDiscount,
            )
        )

    override suspend fun getMayaSwapQuote(
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
        referralCode: String,
    ): SwapQuote =
        maya.fetch(
            MayaQuoteRequest(
                dstAddress = dstAddress,
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
                isAffiliate = isAffiliate,
                bpsDiscount = bpsDiscount,
                referralCode = referralCode,
            )
        )

    override suspend fun getOneInchSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        isAffiliate: Boolean,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson =
        oneInch.fetch(
            OneInchQuoteRequest(
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
                isAffiliate = isAffiliate,
                bpsDiscount = bpsDiscount,
            )
        )

    override suspend fun getKyberSwapQuote(
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        affiliateBps: Int,
    ): EVMSwapQuoteJson =
        kyber.fetch(
            KyberQuoteRequest(
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
                affiliateBps = affiliateBps,
            )
        )

    override suspend fun getLiFiSwapQuote(
        srcAddress: String,
        dstAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
        bpsDiscount: Int,
    ): EVMSwapQuoteJson =
        liFi.fetch(
            LiFiQuoteRequest(
                srcAddress = srcAddress,
                dstAddress = dstAddress,
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
                bpsDiscount = bpsDiscount,
            )
        )

    override suspend fun getJupiterSwapQuote(
        srcAddress: String,
        srcToken: Coin,
        dstToken: Coin,
        tokenValue: TokenValue,
    ): EVMSwapQuoteJson =
        jupiter.fetch(
            JupiterQuoteRequest(
                srcAddress = srcAddress,
                srcToken = srcToken,
                dstToken = dstToken,
                tokenValue = tokenValue,
            )
        )

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? {
        val shared =
            SwapProviderTable.providersFor(srcToken)
                .intersect(SwapProviderTable.providersFor(dstToken))
        return shared.firstOrNull {
            if (srcToken.chain != dstToken.chain)
                it != SwapProvider.ONEINCH && it != SwapProvider.KYBER
            else true
        }
    }
}
