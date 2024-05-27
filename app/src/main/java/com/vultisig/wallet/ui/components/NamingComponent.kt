package com.vultisig.wallet.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NamingComponent(
    title: String = "",
    onSave: () -> Unit = {},
    name: String = "",
    navHostController: NavHostController = rememberNavController(),
    onChangeName: (name: String) -> Unit = {},
    inputTitle: String = "",
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
        bottomBar = {
            Box(Modifier.imePadding()) {
                MultiColorButton(
                    minHeight = 44.dp,
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    text = stringResource(id = R.string.save),
                    onClick = onSave,
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = Theme.montserrat.heading5,
                        fontWeight = FontWeight.Bold,
                        color = Theme.colors.neutral0,
                        modifier = Modifier
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.oxfordBlue800,
                    titleContentColor = Theme.colors.neutral0
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navHostController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "back", tint = Color.White
                        )
                    }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .background(Theme.colors.oxfordBlue800),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = inputTitle,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.body2,
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Theme.colors.oxfordBlue600Main
                    ),
                ) {
                    BasicTextField2(
                        value = name,
                        onValueChange = onChangeName,
                        modifier = Modifier
                            .padding(12.dp)
                            .imePadding(),
                        textStyle = Theme.montserrat.body2.copy(color = Theme.colors.neutral100),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun NamingComponentPreView(){
    NamingComponent()
}