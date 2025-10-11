package com.vultisig.wallet.ui.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.MergeAccount
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.canSelectTokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isDepositSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.NeutralsColors
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

@Immutable
internal data class ChainTokensUiModel(
    val isRefreshing: Boolean = false,
    val chainName: String = "",
    val chainAddress: String = "",
    @DrawableRes val chainLogo: Int? = null,
    val totalBalance: String? = null,
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
    val canDeposit: Boolean = true,
    val canSwap: Boolean = true,
    val canSelectTokens: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val searchTextFieldState: TextFieldState = TextFieldState(),
    val qrCode: BitmapPainter? = null,
)

@Immutable
internal data class ChainTokenUiModel(
    val id: String = "",
    val name: String = "",
    val balance: String? = null,
    val fiatBalance: String? = null,
    val tokenLogo: ImageModel = "",
    val price: String? = null,
    @DrawableRes val chainLogo: Int? = null,
    @DrawableRes val monotoneChainLogo: Int? = null,
    val mergeBalance: String? = null,
    val network: String = "",
)

@HiltViewModel
internal class ChainTokensViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val generateQrBitmap: GenerateQrBitmap,

    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper,
    private val discoverTokenUseCase: DiscoverTokenUseCase,

    private val explorerLinkRepository: ExplorerLinkRepository,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    private val tokens = MutableStateFlow(emptyList<Coin>())
    private val chainRaw: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_CHAIN_ID))
    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))
    private var currentVault: Vault? = null
    private var qrBitmap: Bitmap? = null

    val uiState = MutableStateFlow(ChainTokensUiModel())

    private var loadDataJob: Job? = null

    init {
        viewModelScope.launch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update {
                it.copy(isBalanceVisible = isBalanceVisible)
            }
        }
    }

    fun refresh() {
        loadData()
    }

    fun send() {
        viewModelScope.launch {
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun swap() {
        viewModelScope.launch {
            navigator.route(
                Route.Swap(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun deposit() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Deposit(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun selectTokens() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.SelectTokens(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun openToken(model: ChainTokenUiModel) {
        viewModelScope.launch {
            navigator.route(
                Route.TokenDetail(
                    vaultId = vaultId,
                    chainId = chainRaw,
                    tokenId = model.id,
                    mergeId = model.mergeBalance ?: "0",
                )
            )
        }
    }

    private fun loadData() {
        discoverTokenUseCase(vaultId, chainRaw)

        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            updateRefreshing(true)
            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })
            currentVault = vaultRepository.get(vaultId)
                ?: error("No vault with $vaultId")
            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).combine(fetchMergeBalanceFlow(chain)){ address, mergeBalance ->
                address to mergeBalance
            }.catch {
                updateRefreshing(false)
                Timber.e(it)
            }.onCompletion {
                updateRefreshing(false)
            }.combine(
                uiState.value.searchTextFieldState.textAsFlow()
            ) { (address, mergeBalances), searchQuery ->
                val totalFiatValue = address.accounts
                    .calculateAccountsTotalFiatValue()

                val accounts = address.accounts
                    .sortedWith(
                        compareBy({ !it.token.isNativeToken },
                            { (it.fiatValue?.value ?: it.tokenValue?.decimal)?.unaryMinus() })
                    )

                val tokensFromAccounts = accounts.map { it.token }
                tokens.update { it + tokensFromAccounts }
                val uiTokens = accounts.map { account ->
                    val token = account.token
                    ChainTokenUiModel(
                        id = token.id,
                        name = token.ticker,
                        balance = account.tokenValue
                            ?.let(mapTokenValueToStringWithUnitMapper)
                            ?: "",
                        fiatBalance = account.fiatValue
                            ?.let { fiatValueToStringMapper(it) },
                        tokenLogo = Tokens.getCoinLogo(token.logo),
                        chainLogo = chain.logo,
                        monotoneChainLogo = chain.monoToneLogo,
                        mergeBalance = mergeBalances.findMergeBalance(token).toString(),
                        price = account.price?.let { fiatValueToStringMapper(it) },
                        network = token.chain.raw,
                    )
                }

                val accountAddress = address.address
                val explorerUrl = explorerLinkRepository
                    .getAddressLink(chain, accountAddress)
                val totalBalance = totalFiatValue
                    ?.let { fiatValueToStringMapper(it) }

                val logo = BitmapFactory.decodeResource(
                    context.resources, chain.logo
                )
                val qr = generateQr(accountAddress, logo)


                uiState.update {
                    it.copy(
                        chainName = chainRaw,
                        chainAddress = accountAddress,
                        chainLogo = chain.logo,
                        tokens = uiTokens.filter { uiToken -> searchQuery.isBlank() || uiToken.name.contains(searchQuery, ignoreCase = true) },
                        explorerURL = explorerUrl,
                        totalBalance = totalBalance,
                        canDeposit = chain.isDepositSupported,
                        canSwap = chain.IsSwapSupported,
                        canSelectTokens = chain.canSelectTokens,
                        qrCode = qr
                    )
                }
            }.onCompletion {
                updateRefreshing(false)
            }.collect()
        }
    }

    private suspend fun generateQr(address: String, logo: Bitmap?): BitmapPainter {
        val qrBitmap = withContext(Dispatchers.IO) {
            generateQrBitmap(address, NeutralsColors.Default.n50, Color.Transparent, logo)
        }
        this.qrBitmap = qrBitmap

        val bitmapPainter = BitmapPainter(
            image = qrBitmap.asImageBitmap(),
            filterQuality = FilterQuality.None
        )
        return bitmapPainter
    }

    internal fun shareQRCode(context: Context) {
        val qrBitmap = qrBitmap ?: return
        context.share(
            qrBitmap,
            shareFileName(
                requireNotNull(currentVault),
                ShareType.TOKENADDRESS
            )
        )
    }

    private fun fetchMergeBalanceFlow(
        chain: Chain,
    ): Flow<List<MergeAccount>> = flow {
        emit(emptyList())
        emit(accountsRepository.fetchMergeBalance(chain, vaultId))
    }

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }

    private fun List<MergeAccount>.findMergeBalance(coin: Coin): BigInteger {
        val ticker = coin.ticker.lowercase()

        val mergeBalance = this.firstOrNull {
            it.pool?.mergeAsset?.metadata?.symbol.equals(ticker, true)
        }?.shares?.toBigIntegerOrNull() ?: BigInteger.ZERO

        return mergeBalance
    }
}