package com.voltix.wallet.data.on_board.db

import com.voltix.wallet.models.Vault
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class VaultDB(private val context: Context) {

    private val gson = Gson()
    private val vaultsFolder = context.filesDir.resolve("vaults")

    init {
        // Ensure the vaults folder exists
        vaultsFolder.mkdirs()
    }

    fun upsert(vault: Vault) {
        val file = vaultsFolder.resolve("${vault.name}-vault.dat")
        file.writeText(gson.toJson(vault))
    }

    // Delete operation
    fun delete(vaultName: String) {
        val file = vaultsFolder.resolve("${vaultName}-vault.dat")
        if (file.exists()) {
            file.delete()
        }
    }

    // Select operation
    fun select(vaultName: String): Vault? {
        val file = vaultsFolder.resolve("${vaultName}-vault.dat")
        return if (file.exists()) {
            gson.fromJson(file.readText(), Vault::class.java)
        } else {
            null
        }
    }

    fun selectAll(): MutableList<Vault> {
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
}