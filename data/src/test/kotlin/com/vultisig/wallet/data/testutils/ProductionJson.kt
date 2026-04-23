package com.vultisig.wallet.data.testutils

import com.vultisig.wallet.data.utils.BigDecimalSerializerImpl
import com.vultisig.wallet.data.utils.BigIntegerSerializerImpl
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Builds a [Json] instance that mirrors the Hilt-provided singleton in
 * `com.vultisig.wallet.data.di.DataModule.provideJson` as closely as unit tests allow. Tests that
 * exercise serialization should use this helper so regressions in `ignoreUnknownKeys`,
 * `explicitNulls`, `encodeDefaults`, or the `BigDecimal`/`BigInteger` contextual serializers are
 * caught.
 *
 * The `tss.KeysignResponse` contextual serializer from production is omitted because the native TSS
 * library is not on the JVM unit-test classpath. Tests that depend on that serializer belong in
 * instrumented tests.
 */
fun productionLikeJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    serializersModule = SerializersModule {
        contextual(BigDecimal::class, BigDecimalSerializerImpl())
        contextual(BigInteger::class, BigIntegerSerializerImpl())
    }
}
