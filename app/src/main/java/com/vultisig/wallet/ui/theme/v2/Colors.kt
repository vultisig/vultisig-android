package com.vultisig.wallet.ui.theme.v2

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class Colors(
    val gradients: Gradients = Gradients(),
    val buttons: Buttons = Buttons(),
    val backgrounds: Backgrounds = Backgrounds(),
    val primary: Primary = Primary(),
    val text: Text = Text(),
    val border: Border = Border(),
    val alerts: Alerts = Alerts(),
    val neutrals: Neutrals = Neutrals(),
    val variables: Variables = Variables(),
    val fills: FillsColors = FillsColors(),
    val vibrant: Vibrant = Vibrant(),
)

data class Gradients(
    val primary: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF33E6BF),
            Color(0xFF0439C7),
        )
    ),
    val primaryReversed: Brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0439C7),
            Color(0xFF33E6BF),
        )
    )
)

data class Buttons(
    val primary: Color = Color(0xFF33E6BF),
    val secondary: Color = Color(0xFF061B3A),
    val tertiary: Color = Color(0xFF2155DF),
    val disabled: Color = Color(0xFF0B1A3A),
    val disabledError: Color = Color(0xFF501E1E),
    val ctaPrimary: Color = Color(0xFF0B4EFF),
    val ctaDisabled: Color = Color(0xFF23376D)
)


data class Backgrounds(
    val primary: Color = Color(0xFF02122B),
    val background: Color = Color(0x8002122B),
    val secondary: Color = Color(0xFF061B3A),
    val surface2: Color = Color(0xFF12284A),
    val tertiary: Color = Color(0xFF0B1A3A),
    val tertiary_2: Color = Color(0xFF11284A),
    val success: Color = Color(0xFF042436),
    val alert: Color = Color(0xFF362B17),
    val error: Color = Color(0xFF2B1111),
    val neutral: Color = Color(0xFF061B3A),
    val surface3: Color = Color(0xFF1B2430),
    val surface4: Color = Color(0xFF072C44),
    val light: Color = Color(0xFF11284B),
    val transparent: Color = Color.Transparent,
    val red: Color = Color(0xFFFC070C),
    val body: Color = Color(0xffBBC1C7),
    val amber: Color = Color(0xFFFFB400),
    val teal: Color = Color(0xFF15D7AC),
    val orange: Color = Color(0xffF7961B),
    val disabled: Color = Color(0x800B1A3A)


)

data class Primary(
    val accent1: Color = Color(0xFF042D9A),
    val accent2: Color = Color(0xFF0439C7),
    val accent3: Color = Color(0xFF2155DF),
    val accent4: Color = Color(0xFF4879FD),
    val accent5: Color = Color(0xFF0339C7),
)

data class Text(
    val primary: Color = Color(0xFFF0F4FC),
    val secondary: Color = Color(0xFFC9D6E8),
    val tertiary: Color = Color(0xFF8295AE),
    val inverse: Color = Color(0xFF02122B),
    val button: TextButton = TextButton(),
)

data class TextButton(
    val dark: Color = Color(0xFF02122B),
    val primary: Color = Color(0xFFF0F4FC),
    val disabled: Color = Color(0xFF718096),
    val dim: Color = Color(0xFF5180FC)
)

data class Border(
    val normal: Color = Color(0xFF1B3F73),
    val light: Color = Color(0xFF12284A),
    val extraLight: Color = Color(0xFF02122B),
    val primaryAccent4: Color = Color(0xFF4879FD),
    val disabled: Color = Color(0x992155DF),
)

data class Alerts(
    val success: Color = Color(0xFF13C89D),
    val error: Color = Color(0xFFFF5C5C),
    val warning: Color = Color(0xFFFFC25C),
    val info: Color = Color(0xFF5CA7FF)
)

data class Neutrals(
    val n50: Color = Color(0xFFFFFFFF),
    val n100: Color = Color(0xffF3F4F5),
    val n200: Color = Color(0xFFCBD7E9),
    val n300: Color = Color(0xffBDBDBD),
    val n400: Color = Color(0xffA7A7A7),
    val n500: Color = Color(0xff9F9F9F),
    val n600: Color = Color(0xFF4B5563),
    val n700: Color = Color(0xFF383A40),
    val n800: Color = Color(0xFF0F1011),
    val n900: Color = Color(0xFF000000),

    )

data class Variables(
    val backgroundsSurface1: Color = Color(0xFF061B3A),
    val bordersLight: Color = Color(0xFF11284A),
    val textPrimary: Color = Color(0xFFF0F4FC),
    val buttonsCTAPrimary: Color = Color(0xFF0B4EFF)
)

data class FillsColors(
    val primary: Color = Color(0x1F787880),
)

data class Vibrant(
    val primary: Color = Color(0xFF333333)
)

