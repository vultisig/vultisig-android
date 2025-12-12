package com.vultisig.wallet.data.db.converters

import androidx.room.TypeConverter
import com.vultisig.wallet.data.models.SigningLibType

class SigningLibTypeTypeConverter {

    @TypeConverter
    fun toSigningLibType(value: String): SigningLibType = when (value) {
        DKLS -> SigningLibType.DKLS
        GG20 -> SigningLibType.GG20
        KeyImport -> SigningLibType.KeyImport
        else -> error("Unknown SigningLibType: $value")
    }

    @TypeConverter
    fun fromSigningLibType(value: SigningLibType): String = when (value) {
        SigningLibType.DKLS -> DKLS
        SigningLibType.GG20 -> GG20
        SigningLibType.KeyImport -> KeyImport
    }

    companion object {
        const val DKLS = "DKLS"
        const val GG20 = "GG20"
        const val KeyImport = "KeyImport"
    }

}