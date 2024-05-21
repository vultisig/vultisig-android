package com.vultisig.wallet.data.on_board.db

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.vultisig.wallet.models.Vault
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class VaultDB(private val context: Context) {

    private val gson = Gson()
    private val vaultsFolder = context.filesDir.resolve("vaults")

    init {
        // Ensure the vaults folder exists
        vaultsFolder.mkdirs()
    }

    fun upsert(vault: Vault) {
        val currentFileIndex = vault.fileIndex
        val indexedVault = if (currentFileIndex == 0) {
            val latestFileIndex = selectAll().firstOrNull()?.fileIndex ?: -1
            vault.copy(fileIndex = latestFileIndex + 1)
        } else vault
        val file = vaultsFolder.resolve("${indexedVault.name}${FILE_POSTFIX}")
        file.writeText(gson.toJson(indexedVault))
    }

    private fun upsertIndexed(index: Int, vault: Vault) {
        val indexedVault = vault.copy(fileIndex = index)
        val file = vaultsFolder.resolve("${indexedVault.name}${FILE_POSTFIX}")
        file.writeText(gson.toJson(indexedVault))
    }


    suspend fun updateVaultsFileIndex(vaultList: List<Vault>): List<Vault> = withContext(IO) {
        vaultList.reversed().onEachIndexed { index, vault ->
            if (vault.fileIndex != index)
                upsertIndexed(index, vault)
        }.reversed()
    }

    fun updateVaultName(oldVaultName: String, newVault: Vault) {
        val file = vaultsFolder.resolve("${newVault.name}${FILE_POSTFIX}")
        file.writeText(gson.toJson(newVault))
        if (file.exists() && file.length() > 0) {
            delete(oldVaultName)
        }
    }

    // Delete operation
    fun delete(vaultName: String) {
        val file = vaultsFolder.resolve("${vaultName}${FILE_POSTFIX}")
        if (file.exists()) {
            file.delete()
        }
    }

    // Select operation
    fun select(vaultName: String): Vault? {
        val file = vaultsFolder.resolve("${vaultName}${FILE_POSTFIX}")
        return if (file.exists()) {
            gson.fromJson(file.readText(), Vault::class.java)
        } else {
            null
        }
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
        return allVaults.sortedBy { it.fileIndex }.reversed()
    }

    companion object{
        const val FILE_POSTFIX = "-vault.dat"
    }
}