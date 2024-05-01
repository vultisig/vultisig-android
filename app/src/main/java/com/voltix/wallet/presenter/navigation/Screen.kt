package com.voltix.wallet.presenter.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen(route = "welcome_screen")
    data object Home : Screen(route = "home_screen")

    data object CreateNewVault : Screen(route = "create_new_vault")
    data object JoinKeygen : Screen(route = "join_keygen")
    data object ImportFile : Screen(route = "import_file/{has_file}")
    data object Setup : Screen(route = "setup")
    data object KeygenQr : Screen(route = "keygen_qr")
    data object DeviceList : Screen(route = "device_list/{count}")
    data object Pair : Screen(route = "pair")
    data object GeneratingKeyGen : Screen(route = "generating_key_gen")
    data object SigningError : Screen(route = "signing_error")

}