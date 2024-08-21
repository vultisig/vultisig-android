@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.KeepScreenOn
import com.vultisig.wallet.presenter.keygen.GeneratingKeyViewModel
import com.vultisig.wallet.presenter.keygen.KeygenState
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.PagerCircleIndicator
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCircularProgressIndicator
import com.vultisig.wallet.ui.models.GeneratingKeyWrapperViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun GeneratingKey(
    navController: NavHostController,
    viewModel: GeneratingKeyViewModel,
) {
    KeepScreenOn()
    val wrapperViewModel =
        hiltViewModel(
            creationCallback = { factory: GeneratingKeyWrapperViewModel.Factory ->
                factory.create(viewModel)
            }
        )

    GeneratingKey(
        navController = navController,
        keygenState = wrapperViewModel.viewModel.currentState.collectAsState().value,
        errorMessage = wrapperViewModel.viewModel.errorMessage.value,
    )
}

@Composable
internal fun GeneratingKey(
    navController: NavHostController,
    keygenState: KeygenState,
    errorMessage: String
) {
    val textColor = Theme.colors.neutral0
    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        topBar = {
            TopBar(
                centerText = stringResource(R.string.generating_key_title),
                startIcon = R.drawable.caret_left,
                navController = navController
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 32.dp),
                horizontalAlignment = CenterHorizontally
            ) {
                DevicesOnSameNetworkHint(
                    title = stringResource(R.string.generating_key_screen_keep_devices_on_the_same_wifi_network_with_vultisig_open),
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(all = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (keygenState) {
                KeygenState.CreatingInstance,
                KeygenState.KeygenECDSA,
                KeygenState.KeygenEdDSA,
                KeygenState.ReshareECDSA,
                KeygenState.ReshareEdDSA,
                KeygenState.Success -> {
                    val title = when (keygenState) {
                        KeygenState.CreatingInstance -> stringResource(R.string.generating_key_preparing_vault)
                        KeygenState.KeygenECDSA -> stringResource(R.string.generating_key_screen_generating_ecdsa_key)
                        KeygenState.KeygenEdDSA -> stringResource(R.string.generating_key_screen_generating_eddsa_key)
                        KeygenState.ReshareECDSA -> stringResource(R.string.generating_key_screen_resharing_ecdsa_key)
                        KeygenState.ReshareEdDSA -> stringResource(R.string.generating_key_screen_resharing_eddsa_key)
                        KeygenState.Success -> stringResource(R.string.generating_key_screen_success)
                        else -> ""
                    }

                    val progress = when (keygenState) {
                        KeygenState.CreatingInstance -> 0.25f
                        KeygenState.KeygenECDSA -> 0.5f
                        KeygenState.KeygenEdDSA -> 0.75f
                        KeygenState.ReshareECDSA -> 0.5f
                        KeygenState.ReshareEdDSA -> 0.75f
                        KeygenState.Success -> 1.0f
                        else -> 0F
                    }

                    val isDone = keygenState == KeygenState.Success

                    AnimatedContent(
                        targetState = isDone,
                        label = "Done Checkmark animated content"
                    ) { isDone ->
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = CenterHorizontally,
                        ) {
                            KeygenIndicator(
                                progress = progress,
                            )

                            if (!isDone) {
                                UiSpacer(size = 12.dp)

                                Text(
                                    text = title,
                                    color = Theme.colors.neutral0,
                                    style = Theme.menlo.body2,
                                )

                                UiSpacer(size = 48.dp)

                                val keygenTipTitles =
                                    stringArrayResource(id = R.array.keygen_tip_title)
                                val keygenTipBodys =
                                    stringArrayResource(id = R.array.keygen_tip_body)
                                val keygenTipBodyEmphasis =
                                    stringArrayResource(id = R.array.keygen_tip_body_emphasis)

                                val pagerState = rememberPagerState {
                                    keygenTipTitles.size
                                }

                                HorizontalPager(state = pagerState) { index ->
                                    TipsPage(
                                        title = keygenTipTitles[index],
                                        text = keygenTipBodys[index],
                                        emphasis = keygenTipBodyEmphasis[index]
                                    )
                                }

                                UiSpacer(size = 48.dp)

                                PagerCircleIndicator(
                                    currentIndex = pagerState.currentPage,
                                    size = pagerState.pageCount,
                                )
                            }
                        }
                    }
                }

                KeygenState.ERROR -> {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.generating_key_screen_keygen_failed),
                            color = textColor,
                            style = Theme.menlo.heading5
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = textColor,
                            style = Theme.menlo.body2
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun TipsPage(
    title: String,
    text: String,
    emphasis: String,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle1,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 22.dp)

        val resultText = String.format(text, emphasis)
        val before = resultText.substringBefore(emphasis)
        val after = resultText.substringAfter(emphasis)

        val annotatedText = buildAnnotatedString {
            append(before)
            withStyle(
                style = SpanStyle(
                    color = Theme.colors.turquoise400
                )
            ) {
                append(emphasis)
            }
            append(after)
        }

        Text(
            text = annotatedText,
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(
                    horizontal = 36.dp,
                )
        )
    }
}

@Composable
private fun KeygenIndicator(
    progress: Float,
) {
    val progressAnimated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "KeygenIndicatorProgress"
    )

    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        UiCircularProgressIndicator(
            progress = { progressAnimated },
            strokeWidth = 6.dp,
            modifier = Modifier
                .animateContentSize()
                .then(
                    if (progress > 0.75f) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    } else Modifier.size(74.dp)
                ),
        )
    }
}

@Preview
@Composable
private fun GeneratingKeyPreview() {
    GeneratingKey(
        navController = rememberNavController(),
        keygenState = KeygenState.CreatingInstance,
        errorMessage = ""
    )
}
