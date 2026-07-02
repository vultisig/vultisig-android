package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.send.submit.SendStrategyFactory
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.mockk

/**
 * Builds a [SendStrategyFactory] for unit tests with relaxed mocks for every dependency, with
 * optional overrides for [transactionRepository] and [navigator] which are the only dependencies
 * tests typically verify on.
 */
internal fun fakeSendStrategyFactory(
    transactionRepository: TransactionRepository = mockk(relaxed = true),
    navigator: Navigator<Destination> = mockk(relaxed = true),
): SendStrategyFactory =
    SendStrategyFactory(
        transactionRepository = transactionRepository,
        blockChainSpecificRepository = mockk(relaxed = true),
        feeServiceComposite = mockk(relaxed = true),
        getAvailableTokenBalance = mockk(relaxed = true),
        gasFeeToEstimatedFee = mockk(relaxed = true),
        depositTransactionRepository = mockk(relaxed = true),
        accountsRepository = mockk(relaxed = true),
        chainAccountAddressRepository = mockk(relaxed = true),
        addressParserRepository = mockk(relaxed = true),
        chainValidationService = mockk(relaxed = true),
        navigator = navigator,
    )
