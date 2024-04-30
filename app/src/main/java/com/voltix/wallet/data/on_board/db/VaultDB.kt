package com.voltix.wallet.data.on_board.db
import com.voltix.wallet.models.Vault
import android.content.Context
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicInteger

class VaultDB(private val context: Context) {

    private val nextId = AtomicInteger(1)
    private val gson = Gson()
    private val vaultsFolder = context.filesDir.resolve("vaults")
    init {
        // Ensure the vaults folder exists
        vaultsFolder.mkdirs()
    }

    fun upsert(vault: Vault) {
        val file = vaultsFolder.resolve("${vault.name}-vault.json")
        file.writeText(gson.toJson(vault))
    }

    // Delete operation
    fun delete(vaultName: String) {
        val file = vaultsFolder.resolve("${vaultName}-vault.json")
        if (file.exists()) {
            file.delete()
        }
    }

    // Select operation
    fun select(vaultName: String): Vault? {
        val file = vaultsFolder.resolve("${vaultName}-vault.json")
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
            val vaultJson = file.readText()
            val vault = gson.fromJson(vaultJson, Vault::class.java)
            allVaults.add(vault)
        }
        return allVaults
    }
}