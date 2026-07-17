@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.OPERATION_BOND
import com.vultisig.wallet.data.models.OPERATION_UNBOND
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Cosmos staking deposits (Terra LUNA / LUNC) leave [DepositTransaction.estimateFeesFiat] blank
 * because the fee denom equals the native staking token. The mapper's blank-fiat fallback (#4939)
 * derives the fiat from the native fee so deposits show it like sends do, on both the initiator and
 * a joining device. These tests pin that fallback branch and the normal pass-through.
 */
internal class DepositTransactionUiModelMapperTest {

    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val appCurrencyRepository: AppCurrencyRepository = mockk()

    private fun mapper() =
        DepositTransactionUiModelMapperImpl(
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            fiatValueToStringMapper = fiatValueToStringMapper,
            convertTokenValueToFiat = convertTokenValueToFiat,
            appCurrencyRepository = appCurrencyRepository,
        )

    @Test
    fun `derives network fee fiat from the native fee when estimateFeesFiat is blank`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapTokenValueToStringWithUnit(any()) } returns "0 LUNC"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        // Source value fiat is irrelevant to this branch; only the fee conversion is asserted.
        coEvery { convertTokenValueToFiat(luna, srcValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(luna, feeValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("0.42"), "USD")

        val uiModel = mapper().invoke(transaction(estimateFeesFiat = ""))

        uiModel.networkFeeFiatValue shouldBe "0.42"
    }

    @Test
    fun `keeps the provided estimateFeesFiat when it is not blank`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapTokenValueToStringWithUnit(any()) } returns "0 LUNC"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(luna, srcValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal.ZERO, "USD")

        val uiModel = mapper().invoke(transaction(estimateFeesFiat = "$1.23"))

        uiModel.networkFeeFiatValue shouldBe "$1.23"
    }

    @Test
    fun `title reads Unbonding when the operation is Unbond`() {
        depositVerifyTitleRes(OPERATION_UNBOND) shouldBe R.string.verify_deposit_unbonding
    }

    @Test
    fun `title falls back to the generic sending label for non-Unbond operations`() {
        depositVerifyTitleRes(OPERATION_BOND) shouldBe R.string.verify_deposit_sending
        depositVerifyTitleRes(operation = "") shouldBe R.string.verify_deposit_sending
    }

    @Test
    fun `mapped titleRes reads Unbonding when the operation is Unbond`() = runTest {
        stubMapperDeps()

        val uiModel = mapper().invoke(transaction(operation = OPERATION_UNBOND))

        uiModel.titleRes shouldBe R.string.verify_deposit_unbonding
    }

    @Test
    fun `mapped titleRes ignores an unbond-shaped memo when the operation is not Unbond`() =
        runTest {
            stubMapperDeps()

            // A Custom deposit carries a free-text memo but a blank operation. The mapper must key
            // off the structured operation alone so a memo that happens to start with "UNBOND" is
            // not mislabeled "Unbonding" (#5301). Every real unbond producer sets the operation.
            val uiModel =
                mapper().invoke(transaction(operation = "", memo = "UNBOND:thor1abc:75000000"))

            uiModel.titleRes shouldBe R.string.verify_deposit_sending
        }

    private fun stubMapperDeps() {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapTokenValueToStringWithUnit(any()) } returns "0 LUNC"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(luna, any(), AppCurrency.USD) } returns
            FiatValue(BigDecimal.ZERO, "USD")
    }

    private fun transaction(
        estimateFeesFiat: String = "$1.23",
        operation: String = "",
        memo: String = "",
    ): DepositTransaction =
        DepositTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = luna,
            srcAddress = "terra-src",
            srcTokenValue = srcValue,
            memo = memo,
            dstAddress = "terra-dst",
            estimatedFees = feeValue,
            estimateFeesFiat = estimateFeesFiat,
            operation = operation,
            blockChainSpecific = mockk<BlockChainSpecific>(relaxed = true),
        )

    private companion object {
        val luna =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "LUNC",
                logo = "lunc",
                address = "terra-owner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "terra-luna",
                contractAddress = "",
                isNativeToken = true,
            )
        val srcValue = TokenValue(value = BigInteger("1000000"), token = luna)
        val feeValue = TokenValue(value = BigInteger("25000"), token = luna)
    }
}
