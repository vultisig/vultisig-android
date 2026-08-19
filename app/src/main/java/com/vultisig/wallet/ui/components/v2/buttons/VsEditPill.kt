package com.vultisig.wallet.ui.components.v2.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

/**
 * Pill that opens the edit sheet for whatever the tab next to it lists: the chains of a vault on
 * the home screen, the tokens of a chain on its detail screen.
 *
 * Both surfaces show the same pen and the same chrome, so [label] is the only thing that says which
 * list is about to be edited — it is required rather than defaulted for that reason.
 */
@Composable
internal fun VsEditPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    V2Container(
        modifier = modifier.clickOnce(onClick = onClick),
        type = ContainerType.SECONDARY,
        radius = Theme.v2.radius.pill,
        borderType = ContainerBorderType.Borderless,
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_edit_pencil,
                size = 16.dp,
                tint = Theme.v2.colors.text.primary,
            )

            Text(
                text = label,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewVsEditPill() {
    VsEditPill(label = "Chains", onClick = {})
}
