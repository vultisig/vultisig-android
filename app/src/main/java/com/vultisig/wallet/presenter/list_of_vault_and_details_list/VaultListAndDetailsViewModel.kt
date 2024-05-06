package com.vultisig.wallet.presenter.list_of_vault_and_details_list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.static_data.insertSampleVaultData
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.VaultListAndDetailsEvent.OnItemClick
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.VaultListAndDetailsEvent.UpdateMainScreen
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import javax.inject.Inject

class DetailsItem(logo: Int, coinName: String, assets: String, isAssets: Boolean, value: String, code: String) {
    val logo = logo
    val coinName = coinName
    val assets = assets
    val isAsset = isAssets
    val value = value
    val code = code
}

class CreateItemData(value: String) {
    val value = value
}

@HiltViewModel
class VaultListAndDetailsViewModel @Inject constructor(
    private val vaultDB: VaultDB
) : ViewModel() {

    var listOfVault: MutableList<Vault> = mutableListOf()
    var listOfCoins: MutableList<DetailsItem> = mutableListOf()
    var state by mutableStateOf(VaultListAndDetailsState())
        private set

    init {
        onEvent(UpdateMainScreen(true))
    }

    fun onEvent(event: VaultListAndDetailsEvent) {
        when (event) {
            is OnItemClick -> updateData(event.value)
            is UpdateMainScreen -> updateScreen(event.isVisible)
        }
    }

    fun onVaultItemClick(index: Int) {
        onEvent(OnItemClick(listOfVault[index]))
        onEvent(UpdateMainScreen(false))
    }

    private fun updateScreen(visible: Boolean) {
        if (visible) {
            state = state.copy(loadingData = true, centerText = "Vaults")
            //todo - remove it on real release
            if (vaultDB.selectAll().isEmpty())
                insertSampleVaultData(vaultDB);
            listOfVault = vaultDB.selectAll();
            state = state.copy(
                isMainListVisible = visible,
                loadingData = false, listOfVaultNames = listOfVault,
                centerText = "Vaults")

        }else {
            state = state.copy(
                isMainListVisible = visible,
                centerText = state.selectedItem?.name ?: ""
            )
        }
    }

    private fun updateData(value: Vault) {
        state = state.copy(selectedItem = value, loadingData = true)
        listOfCoins.clear();
        value.coins.groupBy(Coin::chain).mapValues { entry ->
            if (entry.value.count() == 1) {
                listOfCoins.add(
                    DetailsItem(
                        logo = entry.value[0].logo.toInt(),
                        coinName = entry.value[0].ticker,
                        assets = createAssetsOrValue(entry.value[0].rawBalance, entry.value[0].decimal),
                        value = createFormat( createTotalPrice(
                            entry.value[0].priceRate,
                            entry.value[0].rawBalance,
                            entry.value[0].decimal
                        )),
                        isAssets = false,
                        code = entry.value[0].address,
                    )
                )
            } else {
                listOfCoins.add(
                    DetailsItem(
                        logo = entry.value[0].logo.toInt(),
                        coinName = entry.value[0].ticker,
                        assets = entry.value.count().toString() + " assets",
                        value =   createFormat(sumOfTotalPrice(entry.value)),
                        isAssets = false,
                        code = entry.value[0].address,
                    )
                )
            }
        }
        state = state.copy(listOfVaultCoins = listOfCoins, selectedItem = value, loadingData = false)

    }

    private fun createAssetsOrValue(rawBalance: BigInteger, decimal: Int): String {
        val decimalBig = BigInteger.valueOf(decimal.toLong())
        var divValue = rawBalance.toDouble() / decimalBig.toDouble()
        return DecimalFormat("##.##")
//            .apply { roundingMode = RoundingMode.FLOOR }
            .format(divValue)
    }

    private fun createTotalPrice(priceRate: BigDecimal, rawBalance: BigInteger, decimal: Int): Double {
        val mul = priceRate.toDouble() * rawBalance.toDouble()
        var divValue = mul / decimal.toDouble()
        return divValue
    }

    private  fun sumOfTotalPrice( coins :List<Coin>) :Double{
        var sumOf=0.0
        coins.map  {
            sumOf +=  createTotalPrice(it.priceRate, it.rawBalance, it.decimal)
        }
        return sumOf;
    }

    private fun createFormat(value: Double): String {
        return "$" + DecimalFormat("##.###")
//            .apply { roundingMode = RoundingMode.CEILING }
            .format(value)
    }

    fun currencyFormat(amount: String): String {
        val formatter = DecimalFormat("###,##0.00")
        return formatter.format(amount.toDouble())
    }
}