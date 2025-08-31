package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.LockTimeUiState
import com.vultisig.wallet.ui.models.settings.LockTimeViewModel
import com.vultisig.wallet.ui.theme.Theme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


@Composable
internal fun LockTimeScreen() {
    val viewmodel: LockTimeViewModel = hiltViewModel()
    val uiState = viewmodel.uiState.collectAsState()
    LockTimeScreen(
        onBackClick = viewmodel::back,
        onLockTimeSelected = viewmodel::onLockTimeSelected,
        uiState = uiState.value
    )
}


@Composable
private fun LockTimeScreen(
    onBackClick: () -> Unit,
    onLockTimeSelected: (Duration?) -> Unit,
    uiState: LockTimeUiState,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Lock Time",
                onBackClick = onBackClick
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                )
                .verticalScroll(
                    state = rememberScrollState(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = "Lock Vultisig automatically after...",
                color = Theme.colors.text.light,
                style = Theme.brockmann.supplementary.footnote,
            )

            uiState.lockTimes.forEach { time ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Theme.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(
                                size = 8.dp
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = Theme.colors.borders.light,
                            shape = RoundedCornerShape(
                                size = 8.dp
                            ),
                        )
                        .padding(
                            all = 16.dp,
                        )
                        .clickable(
                            onClick = {
                                onLockTimeSelected(time)
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween
                ) {
                    Text(
                        text = time.formatted(),

                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.colors.text.primary,
                    )

                    if (uiState.selectedLockTime == time) {
                        Image(
                            painter = painterResource(
                                R.drawable.check_2
                            ),
                            contentDescription = null,
                        )
                    }

                }
            }

        }
    }
}

@Composable
private fun Duration?.formatted(): String {
    return when (this) {
        null -> stringResource(R.string.never)
        else -> {
            when {
                inWholeSeconds < 1.minutes.inWholeSeconds -> stringResource(
                    R.string.seconds_format,
                    inWholeSeconds
                )

                inWholeMinutes < 1.hours.inWholeMinutes -> stringResource(
                    if (inWholeMinutes == 1L)
                        R.string.minute_format
                    else R.string.minutes_format,
                    inWholeMinutes
                )

                inWholeHours < 1.days.inWholeHours -> stringResource(
                    if (inWholeHours == 1L)
                        R.string.hour_format
                    else R.string.hours_format,
                    inWholeHours
                )

                else -> error("this should not happen")
            }
        }
    }
}

@Preview
@Composable
internal fun LockTimeScreenPreview() {
    LockTimeScreen(
        uiState = LockTimeUiState(),
        onBackClick = {},
        onLockTimeSelected = {},
    )
}
