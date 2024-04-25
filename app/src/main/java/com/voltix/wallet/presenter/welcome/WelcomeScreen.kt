package com.voltix.wallet.presenter.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.domain.on_board.models.OnBoardPage
import com.voltix.wallet.presenter.common.UiEvent
import com.voltix.wallet.presenter.welcome.WelcomeEvent.BoardCompleted
import MultiColorButton


@OptIn(ExperimentalFoundationApi::class)
@ExperimentalAnimationApi
@Composable
fun WelcomeScreen(
    navController: NavHostController,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val pages = viewModel.state.pages
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(key1 = Unit) {
        viewModel.channel.collect { uiEvent ->
            when (uiEvent) {
                is UiEvent.NavigateTo -> {
                    navController.navigate(uiEvent.screen.route)
                }
                is UiEvent.ScrollToNextPage ->{
                    if (pagerState.currentPage<2){
                        pagerState.scrollToPage(pagerState.currentPage+1)
                    }
                    else{
                        navController.popBackStack();
                        navController.navigate(uiEvent.screen.route);

                    }
                }

                UiEvent.PopBackStack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.appColor.oxfordBlue800)
    ) {
        HorizontalPager(
            modifier = Modifier.weight(9f),
            state = pagerState,
            verticalAlignment = Alignment.Top
        ) { position ->
            PagerScreen(onBoardingPage = pages[position],navController)
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
            .weight(0.3f))
        MultiColorButton(
            text = "Next",
            minHeight = MaterialTheme.dimens.minHeightButton,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.minHeightButton,
                    end = MaterialTheme.dimens.buttonMargin
                )
//            pagerState = pagerState
        ) {
            viewModel.onEvent(WelcomeEvent.NextPages)
        }
        Spacer(modifier = Modifier
            .weight(0.3f))
        if (pagerState.currentPage<2){
            MultiColorButton(
                text = "Skip",
                backgroundColor = MaterialTheme.appColor.oxfordBlue800,
                textColor = MaterialTheme.appColor.turquoise800,
                iconColor = MaterialTheme.appColor.oxfordBlue800,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                viewModel.onEvent(BoardCompleted)

            }
        }
    }
}

@Composable
fun PagerScreen(onBoardingPage: OnBoardPage,navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .fillMaxHeight(0.2f),
            painter = painterResource(R.drawable.voltix_icon_text),
            contentDescription = "Pager Image"
        )
        Spacer(Modifier.height(20.dp))
        Image(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.5f),
            painter = painterResource(id = onBoardingPage.image),
            contentDescription = "Pager Image"
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(top = 20.dp),
            text = onBoardingPage.description,
            style = MaterialTheme.montserratFamily.bodyLarge,
            color =MaterialTheme.appColor.neutral0 ,
            textAlign = TextAlign.Center
        )

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