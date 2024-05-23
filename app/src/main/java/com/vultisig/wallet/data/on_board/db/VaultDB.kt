package com.vultisig.wallet.data.on_board.db

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.vultisig.wallet.models.Vault

@Deprecated("Use VaultRepository instead")
class VaultDB(context: Context) {

    private val gson = Gson()
    private val vaultsFolder = context.filesDir.resolve("vaults")

    init {
        // Ensure the vaults folder exists
        vaultsFolder.mkdirs()
    }

    fun selectAll(): List<Vault> {
        val allVaults = mutableListOf<Vault>()
        val vaultFiles = vaultsFolder.listFiles()
        vaultFiles?.forEach { file ->
            try {
                val vaultJson = file.readText()
                val vault = gson.fromJson(vaultJson, Vault::class.java)
                allVaults.add(vault)
            } catch (e: JsonSyntaxException) {
                // Log the error or handle it as needed
                println("Skipping file ${file.name}: not a valid Vault JSON")
            } catch (e: Exception) {
                // Handle other unexpected exceptions
                println("Unexpected error while processing file ${file.name}: ${e.message}")
            }
        }
        return allVaults
    }

    companion object {
        const val FILE_POSTFIX = "-vault.dat"
    }
}