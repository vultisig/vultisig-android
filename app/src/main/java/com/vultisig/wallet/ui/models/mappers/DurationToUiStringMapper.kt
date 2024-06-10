package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.mappers.MapperFunc
import javax.inject.Inject
import kotlin.time.Duration

internal interface DurationToUiStringMapper : MapperFunc<Duration, String>

internal class DurationToUiStringMapperImpl @Inject constructor() : DurationToUiStringMapper {

    override fun invoke(from: Duration): String =
        from.toString()

}