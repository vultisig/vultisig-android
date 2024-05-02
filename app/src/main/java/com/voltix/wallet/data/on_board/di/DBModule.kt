import android.content.Context
import com.voltix.wallet.data.on_board.db.VaultDB

interface DatabaseModule {
    fun provideVaultDatabase(context: Context): VaultDB
}

object DefaultDatabaseModule : DatabaseModule {
    override fun provideVaultDatabase(context: Context): VaultDB {
        return VaultDB(context)
    }
}