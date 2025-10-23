package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BondedTabs(
    tabs: List<String>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    content: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tabs.forEach { tab ->
                Column(
                    horizontalAlignment = CenterHorizontally,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onTabSelected(tab) }
                ) {
                    Text(
                        text = tab,
                        color = if (tab == selectedTab) {
                            Theme.v2.colors.text.primary
                        } else {
                            Theme.v2.colors.text.extraLight
                        },
                        style = Theme.brockmann.body.s.medium,
                    )

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(
                                color = if (tab == selectedTab) {
                                    Theme.v2.colors.primary.accent4
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }

        if (content != null) {
            content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1628)
@Composable
private fun PreviewBondedTabsWithContent() {
    var selectedTab by remember { mutableStateOf("Staked") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        BondedTabs(
            tabs = listOf("Bonded", "Staked", "LPs"),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            content = {
                // Example edit button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF121A2E), CircleShape)
                        .clickable { /* Edit action */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✏",
                        color = Color(0xFF3D6EFF),
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1628)
@Composable
private fun PreviewBondedTabs() {
    var selectedTab by remember { mutableStateOf("Bonded") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        BondedTabs(
            tabs = listOf("Bonded", "Staked", "LPs"),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}
