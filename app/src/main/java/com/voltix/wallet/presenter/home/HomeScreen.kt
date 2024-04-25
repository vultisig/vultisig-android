package com.voltix.wallet.presenter.home

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.voltix.wallet.presenter.create_new_vault.CreateNewVault

@Composable
fun HomeScreen(navController: NavHostController) {
    CreateNewVault(navController)
}
/*


@Composable
fun CreateNewVault(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {
        Image(
            painter = painterResource(id = R.drawable.question),
            contentDescription = "question",
            modifier = Modifier
                .align(TopEnd)
                .padding(19.dp)
        )
        Column(
            modifier = Modifier.align(Center), horizontalAlignment = CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.voltix), contentDescription = "voltix"
            )
            Text(
                text = "Voltix",
                color = textColor,
                style = MaterialTheme.montserratFamily.headlineLarge.copy(fontSize = 50.sp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "SECURE CRYPTO VAULT", color = textColor,
                style = MaterialTheme.montserratFamily.bodySmall.copy(
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold
                ),
            )
        }
        Column(
            modifier = Modifier
                .align(BottomCenter)
                .padding(20.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            Button(onClick = {
                navController.navigate(route = Screen.Setup.route)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Create a New Vault")
            }
            Button(onClick = {
                navController.navigate(Screen.ImportFile.route)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Import an Existing Vault")
            }

        }
    }
}

@Composable
fun ImportFile(navController: NavHostController, hasFile: Boolean) {
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar("Import")
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Enter your previously created vault share",
            color = textColor,
            style = MaterialTheme.menloFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(MaterialTheme.dimens.small2))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            .clickable {
                navController.navigate(Screen.ImportFile.route.replace(oldValue = "{has_file}", newValue = true.toString()))
            }
            .drawBehind {
                drawRoundRect(
                    color = Color("#33e6bf".toColorInt()), style = Stroke(
                        width = 8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f), 0f)
                    ), cornerRadius = CornerRadius(16.dp.toPx())
                )
            }

        ) {
            Column(Modifier.align(Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painterResource(id = R.drawable.file), contentDescription = "file"
                )
                Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
                Text(
                    text = "Upload file, text or image",
                    color = textColor,
                    style = MaterialTheme.menloFamily.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        if (hasFile)
            LazyColumn {
                item {
                    Row(verticalAlignment = CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.file),
                            contentDescription = "file icon",
                            modifier = Modifier.width(MaterialTheme.dimens.medium1)
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.dimens.small1))
                        Text(
                            text = "voltix-vault-share-jun2024.txt",
                            color = textColor,
                            style = MaterialTheme.menloFamily.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1.0f))
                        Icon(
                            painter = painterResource(id = R.drawable.x),
                            contentDescription = "X",
                            tint = textColor
                        )

                    }
                }
            }
        Spacer(modifier = Modifier.weight(1.0F))
        Button(
            onClick = {
                      navController.navigate(Screen.Setup.route)
            }, enabled = hasFile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors()
                .copy(disabledContainerColor = MaterialTheme.colorScheme.surfaceDim),
        ) {
            Text(
                text = "Continue",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.montserratFamily.titleMedium,
            )
        }
    }
}

@Composable
fun TopBar(
    centerText: String,
    modifier: Modifier = Modifier,
    @DrawableRes startIcon: Int? = null,
    @DrawableRes endIcon: Int? = null,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        startIcon?.let { id ->
            Image(painter = painterResource(id = id), contentDescription = null)
        } ?: Spacer(modifier = Modifier)
        Text(
            text = centerText,
            color = textColor,
            style = MaterialTheme.montserratFamily.headlineMedium.copy(fontSize = MaterialTheme.dimens.medium1.value.sp)
        )
        endIcon?.let { id ->
            Image(painter = painterResource(id = id), contentDescription = null)
        } ?: Spacer(modifier = Modifier)
    }
}

@Composable
fun Setup(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(
            centerText = "Setup", startIcon = R.drawable.caret_left, endIcon = R.drawable.question
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Text(
            text = "2 of 3 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "(Any 3 Devices)",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Image(
            painter = painterResource(id = R.drawable.devices),
            contentDescription = "devices",
            modifier = Modifier.width(140.dp)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "3 Devices to create a vault; ",
            color = textColor
        )
        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "2 devices to sign a transaction.",
            color = textColor
        )
        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "Automatically backed-up",
            color = textColor
        )

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = R.drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with VOLTIX open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))
        Button(onClick = {
            navController.navigate(Screen.KeygenQr.route)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start")
        }
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Button(onClick = {
                         navController.navigate(Screen.Pair.route)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Join")
        }
    }
}


@Composable
fun KeygenQr(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Text(
            text = "2 of 3 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "Pair with other devices:",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Image(painter = painterResource(id = R.drawable.qr),
            contentScale = ContentScale.FillBounds,
            contentDescription = "devices",
            modifier = Modifier
                .width(150.dp)
                .height(150.dp)

                .drawBehind {
                    drawRoundRect(
                        color = Color("#33e6bf".toColorInt()), style = Stroke(
                            width = 8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0.0f)
                        ), cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
                .padding(20.dp))

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Spacer(
            modifier = Modifier.height(MaterialTheme.dimens.small1)
        )
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            DeviceInfo(R.drawable.ipad, "iPad", "1234h2i34h")
            Spacer(modifier = Modifier.width(MaterialTheme.dimens.large))
            DeviceInfo(R.drawable.iphone, "iPhone", "623654ghdsg")
        }

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = R.drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with VOLTIX open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))
        Button(onClick = {
            navController.navigate(
                Screen.DeviceList.route.replace(
                    oldValue = "{count}", newValue = "2"
                )
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start")
        }
    }
}


@Composable
fun DeviceList(navController: NavHostController, itemCount: Int = 4) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val items = listOf(
        "1- iPad Pro 6th generation (This Device)",
        "2- iPad Pro (Pair Device)",
        "3- iPad Pro (Pair Device)",
        "4- iPad Pro (Backup Device)",
    ).subList(0, itemCount)
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(centerText = "Keygen", startIcon = R.drawable.caret_left)

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))

        Text(
            text = "2 of 2 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "With these devices",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        LazyColumn {
            items.forEach {
                item {
                    DeviceInfoItem(it)
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Text(
            style = MaterialTheme.menloFamily.bodyMedium,
            text = "You can only send transactions with these two devices present.",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))
        Text(
            style = MaterialTheme.menloFamily.bodyMedium,
            text = "You do not have a 3rd backup device - so you should backup one vault share securely later.",
            color = textColor
        )


        Spacer(modifier = Modifier.weight(1.0f))


        Button(onClick = {
            if (itemCount + 1 <= 4) {
                navController.navigate(
                    Screen.DeviceList.route.replace(
                        oldValue = "{count}", newValue = "${itemCount + 1}"
                    )
                )
            } else {
                navController.navigate(
                    Screen.GeneratingKeyGen.route
                )
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Continue")
        }
    }
}


@Composable
fun Pair(navController: NavHostController) {
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(vertical = MaterialTheme.dimens.medium1)
    ) {
        TopBar(centerText = "Pair", startIcon = R.drawable.caret_left)

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxSize()
                .background(neutral900)
        ) {
            Box(modifier = Modifier
                .align(Center)
                .width(250.dp)
                .height(250.dp)
                .clip(RoundedCornerShape(MaterialTheme.dimens.medium1))

                .drawBehind {
                    drawRoundRect(
                        color = Color("#33e6bf".toColorInt()), style = Stroke(
                            width = 8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0f)
                        ), cornerRadius = CornerRadius(16.dp.toPx())
                    )
                })
        }
    }
}


@Composable
fun DeviceInfo(@DrawableRes icon: Int, name: String, info: String) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = MaterialTheme.dimens.small1))
            .background(oxfordBlue600Main)
            .padding(
                vertical = MaterialTheme.dimens.small1, horizontal = MaterialTheme.dimens.medium1
            ),

        horizontalAlignment = CenterHorizontally
    ) {
        Image(painter = painterResource(id = icon), contentDescription = "ipad")
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = name, color = textColor, style = MaterialTheme.montserratFamily.titleMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = info, color = textColor, style = MaterialTheme.montserratFamily.titleSmall
        )
    }
}

@Composable
fun GeneratingKeyGen(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(centerText = "Keygen")

        Spacer(modifier = Modifier.weight(1.0f))
        Text(text = "generating", color = textColor, style = MaterialTheme.menloFamily.bodyMedium)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Image(
            painterResource(id = R.drawable.generating),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.weight(1.0f))


        Icon(
            painter = painterResource(id = R.drawable.wifi),
            contentDescription = null,
            tint = neutral0
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with VOLTIX open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
    }
}


@Composable
fun SigningError(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(centerText = "Keygen")

        Spacer(modifier = Modifier.weight(1.0f))
        Image(
            painterResource(id = R.drawable.danger),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        Text(
            text = "Signing Error. Please try again.", color = textColor,
            style = MaterialTheme.menloFamily.bodyMedium.copy(
                textAlign = TextAlign.Center, lineHeight = 25.sp
            ),
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),

            )

        Spacer(modifier = Modifier.weight(1.0f))

        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.small1),
            text = "Keep devices on the same WiFi Network, correct vault and pair devices. \n" + "Make sure no other devices are \n" + "running Voltix.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Button(onClick = { */
/*TODO*//*
 }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Try Again")
        }
    }
}*/
