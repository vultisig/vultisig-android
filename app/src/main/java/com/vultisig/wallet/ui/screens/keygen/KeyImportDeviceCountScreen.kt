package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.KeyImportDeviceCountViewModel
import com.vultisig.wallet.ui.models.keygen.KeyImportSetupType
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun KeyImportDeviceCountScreen(
    model: KeyImportDeviceCountViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    V2Scaffold(
        onBackClick = model::back,
        title = stringResource(R.string.key_import_device_count_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = CenterHorizontally,
        ) {
            UiSpacer(24.dp)

            val isSecure = state.selectedType is KeyImportSetupType.Secure

            LookaheadScope {
                Box(
                    modifier = Modifier
                        .height(intrinsicSize = IntrinsicSize.Min)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.backgrounds.tertiary_2)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = if (isSecure)
                            Alignment.TopEnd else Alignment.TopStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .animatePlacementInScope(this@LookaheadScope)
                                .clip(CircleShape)
                                .background(Theme.v2.colors.backgrounds.primary)
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable {
                                    model.selectType(KeyImportSetupType.Fast)
                                }
                                .padding(16.dp)
                                .wrapContentWidth(CenterHorizontally),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.thunder),
                                contentDescription = null,
                                tint = Theme.v2.colors.text.primary,
                            )
                            UiSpacer(8.dp)
                            Text(
                                text = stringResource(R.string.key_import_device_count_fast),
                                color = Theme.v2.colors.text.primary,
                                style = Theme.brockmann.body.s.medium,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable {
                                    model.selectType(KeyImportSetupType.Secure)
                                }
                                .padding(16.dp)
                                .wrapContentWidth(CenterHorizontally),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shield),
                                contentDescription = null,
                                tint = if (isSecure)
                                    Theme.v2.colors.alerts.success
                                else Theme.v2.colors.text.primary,
                            )
                            UiSpacer(8.dp)
                            Text(
                                text = stringResource(R.string.key_import_device_count_secure),
                                color = Theme.v2.colors.text.primary,
                                style = Theme.brockmann.body.s.medium,
                            )
                        }
                    }
                }
            }

            UiSpacer(32.dp)

            val shape = RoundedCornerShape(15)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = shape,
                    )
                    .background(Theme.v2.colors.backgrounds.tertiary_2)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.selectedType) {
                    KeyImportSetupType.Fast -> {
                        DescriptionItem(stringResource(R.string.key_import_device_count_fast_desc_1))
                        DescriptionItem(stringResource(R.string.key_import_device_count_fast_desc_2))
                        DescriptionItem(stringResource(R.string.key_import_device_count_fast_desc_3))
                    }
                    KeyImportSetupType.Secure -> {
                        DescriptionItem(stringResource(R.string.key_import_device_count_secure_desc_1))
                        DescriptionItem(stringResource(R.string.key_import_device_count_secure_desc_2))
                        DescriptionItem(stringResource(R.string.key_import_device_count_secure_desc_3))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            VsButton(
                label = stringResource(R.string.key_import_device_count_get_started),
                onClick = model::getStarted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DescriptionItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = Theme.v2.colors.alerts.success,
        )
        Text(
            text = text,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}
