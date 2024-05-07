package com.vultisig.wallet.presenter.welcome

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.data.on_board.models.OnBoardPage
import com.vultisig.wallet.presenter.base_components.MultiColorButton
import com.vultisig.wallet.presenter.common.UiEvent.NavigateTo
import com.vultisig.wallet.presenter.common.UiEvent.PopBackStack
import com.vultisig.wallet.presenter.common.UiEvent.ScrollToNextPage


@OptIn(ExperimentalFoundationApi::class)
@ExperimentalAnimationApi
@Composable
fun WelcomeScreen(
    navController: NavHostController,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val pages = viewModel.state.pages
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(key1 = Unit) {
        viewModel.channel.collect { uiEvent ->
            when (uiEvent) {
                is NavigateTo -> {
                    navController.navigate(uiEvent.screen.route)
                }

                is ScrollToNextPage -> {
                    if (pagerState.currentPage < 2)
                        pagerState.scrollToPage(pagerState.currentPage + 1)
                    else {
                        navController.popBackStack()
                        navController.navigate(uiEvent.screen.route)
                    }

                }

                PopBackStack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800),
    ) {
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginLarge))
        Image(
            painter = painterResource(R.drawable.vultisig_icon_text),
            contentDescription = "Pager Image"
        )
        HorizontalPager(
            modifier = Modifier.weight(9f),
            state = pagerState,
            verticalAlignment = Alignment.Top
        ) { position ->
            PagerScreen(onBoardingPage = pages[position])
        }
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration)
                    MaterialTheme.appColor.turquoise800
                else
                    MaterialTheme.appColor.oxfordBlue200

                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier
            .height(MaterialTheme.dimens.marginSmall))
        MultiColorButton(
            text = stringResource(id = R.string.next),
            minHeight = MaterialTheme.dimens.minHeightButton,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.buttonMargin,
                    end = MaterialTheme.dimens.buttonMargin
                )
        ) {
            viewModel.onEvent(WelcomeEvent.NextPages)
        }
        Spacer(
            modifier = Modifier
                .weight(0.3f)
        )

        MultiColorButton(
            text = stringResource(id = R.string.skip),
            backgroundColor = MaterialTheme.appColor.oxfordBlue800,
            textColor = MaterialTheme.appColor.turquoise800,
            iconColor = MaterialTheme.appColor.oxfordBlue800,
            minHeight = MaterialTheme.dimens.minHeightButton,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (pagerState.currentPage < 2) 1.0f else 0.0f)
        ) {
            viewModel.onEvent(WelcomeEvent.BoardCompleted)
        }

        Spacer(
            modifier = Modifier
                .height(MaterialTheme.dimens.marginMedium)
        )

    }
}

@Composable
fun PagerScreen(onBoardingPage: OnBoardPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(1.0f))
        Image(
            painter = painterResource(id = onBoardingPage.image),
            contentDescription = "Pager Image"
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginExtraLarge))
        Text(
            modifier = Modifier
                .fillMaxWidth(0.7f),
            text = stringResource(id = onBoardingPage.description),
            style = MaterialTheme.montserratFamily.bodyLarge.copy(lineHeight = 30.sp),
            color =MaterialTheme.appColor.neutral0 ,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1.0f))
    }
}

//@OptIn(ExperimentalFoundationApi::class)
//@ExperimentalAnimationApi
//@Composable
//fun FinishButton(
//    modifier: Modifier,
//    pagerState: PagerState,
//    onClick: () -> Unit
//) {
//    Row(
//        modifier = modifier
//            .padding(horizontal = 40.dp),
//        verticalAlignment = Alignment.Top,
//        horizontalArrangement = Arrangement.Center
//    ) {
//        AnimatedVisibility(
//            modifier = Modifier.fillMaxWidth(),
//            visible = pagerState.currentPage == 2
//        ) {
//            Button(
//                onClick = onClick,
//                colors = ButtonDefaults.buttonColors(
//                    contentColor = Color.White
//                )
//            ) {
//                Text(text = "Finish")
//            }
//        }
//    }
//}
