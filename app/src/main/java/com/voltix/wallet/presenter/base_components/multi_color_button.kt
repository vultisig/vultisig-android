import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.montserratFamily

@Composable
public fun multiColorButton(
    text: String="",
    startIcon: Int?=null,
    trailingIcon: Int?=null,
    iconColor:  Color?=null,
    backgroundColor: Color?=null,
    foregroundColor: Color?=null,
    borderColor: Color?=null,
    iconSize: Dp?=null,
    borderSize: Dp?=null,
    minWidth: Dp?=null,
    minHeight: Dp?=null,
    textStyle: TextStyle?=null,
    textColor:  Color?=null,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = borderSize ?: MaterialTheme.dimens.extraSmall,
//                color = borderColor ?: MaterialTheme.appColor.transparent,
                brush = Brush.horizontalGradient(
                    listOf(MaterialTheme.appColor.turquoise600Main
                        , MaterialTheme.appColor.persianBlue600Main)),
                shape = CircleShape

            )
            .clip(shape = RoundedCornerShape(60.dp))
            .background(
                color = backgroundColor ?: MaterialTheme.appColor.turquoise600Main,
            )
            .defaultMinSize(
                minWidth = minWidth ?: MaterialTheme.dimens.minWidth
                , minHeight = minHeight ?: MaterialTheme.dimens.medium3
            )

    ) {
        Icon(
            painter = painterResource(startIcon ?: R.drawable.check),
            contentDescription = null,
            tint = iconColor ?: MaterialTheme.appColor.turquoise600Main,
            modifier = Modifier.size(iconSize ?: MaterialTheme.dimens.large)
        )
        Text(
            text = text,
            color = textColor ?: MaterialTheme.appColor.oxfordBlue600Main,
            style = textStyle ?: MaterialTheme.montserratFamily.titleLarge
        )
        Icon(
            painter = painterResource(trailingIcon ?: R.drawable.check),
            contentDescription = null,
            tint = iconColor ?: MaterialTheme.appColor.turquoise600Main,
            modifier = Modifier.size(iconSize ?: MaterialTheme.dimens.large)
        )
    }
}