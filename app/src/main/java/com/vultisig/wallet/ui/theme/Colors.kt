package com.vultisig.wallet.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal data class Colors(
    val transparent: Color = Color(0x00000000),
    val transparentWhite: Color = Color(0x00FFFFFF),

    val neutral0: Color = Color(0xffFFFFFF),
    val neutral100: Color = Color(0xffF3F4F5),
    val neutral200: Color = Color(0xffCBD7E9),
    val neutral300: Color = Color(0xffBDBDBD),
    val neutral400: Color = Color(0xffA7A7A7),
    val neutral500: Color = Color(0xff9F9F9F),
    val neutral600: Color = Color(0xff777777),
    val neutral700: Color = Color(0xff3E3E3E),
    val neutral800: Color = Color(0xff101010),
    val neutral900: Color = Color(0xff000000),

    val body: Color = Color(0xffBBC1C7),

    val ultramarine: Color = Color(0xff390F94),
    val mediumPurple: Color = Color(0xff9563FF),
    val error: Color = Color(0xffFFB400),
    val darkPurpleMain: Color = Color(0xff0F0623),
    val darkPurple800: Color = Color(0xff1E103E),
    val darkPurple500: Color = Color(0xff3B2D59),
    val tallShips: Color = Color(0xff0D86BB),
    val aquamarine: Color = Color(0xff33E6BF),
    val sapphireGlitter: Color = Color(0xff0439C7),

    val transparentOxfordBlue: Color = Color(0xFF1A2339),
    val oxfordBlue800: Color = Color(0xff02122B),
    val oxfordBlue600Main: Color = Color(0xff061B3A),
    val oxfordBlue400: Color = Color(0xff11284A),
    val oxfordBlue200: Color = Color(0xff1B3F73),

    val persianBlue800: Color = Color(0xff042D9A),
    val persianBlue600Main: Color = Color(0xff0439C7),
    val persianBlue400: Color = Color(0xff2155DF),
    val persianBlue200: Color = Color(0xff4879FD),

    val trasnparentTurquoise: Color = Color(0x8015D7AC),
    val turquoise800: Color = Color(0xff15D7AC),
    val turquoise600Main: Color = Color(0xff33E6BF),
    val turquoise400: Color = Color(0xff81F8DE),
    val turquoise200: Color = Color(0xffA6FBE8),

    val transparentRed: Color = Color(0x59DA2E2E),
    val red: Color = Color(0xffFF4040),
    val alertBackground: Color = Color(0x59DA2E2E),
    val alert: Color = Color(0xffDA2E2E),
    val miamiMarmalade: Color = Color(0xffF7961B),
    val miamiMarmaladeFaded: Color = Color(0x59F7961B),
    val approval: Color = Color(0xff31CF59),
    val approvalFaded: Color = Color(0x5931CF59),


    // new design system
    val buttons: ButtonsColors = ButtonsColors(),
    val iconButtons: IconButtonsColors = IconButtonsColors(),
    val backgrounds: BackgroundsColors = BackgroundsColors(),
    val primary: PrimaryColors = PrimaryColors(),
    val text: TextColors = TextColors(),
    val borders: BordersColors = BordersColors(),
    val buttonBorders: ButtonBorderColors = ButtonBorderColors(),
    val alerts: AlertsColors = AlertsColors(),
    val neutrals: NeutralsColors = NeutralsColors.Default,
    val fills: FillsColors = FillsColors(),
    val vibrant: Vibrant = Vibrant(),

    val gradients: Gradients = Gradients(),

) {
    companion object {
        val Default = Colors()
    }
}

internal data class ButtonsColors(
    val primary: Color = Color(0xFF2155DF),
    val secondary: Color = Color(0xFF02122B),
    val disabledPrimary: Color = Color(0xFF0B1A3A),
    val disabledSecondary: Color = Color(0xFF02122B),
)

internal data class IconButtonsColors(
    val primary: Color = Color(0xFF33E6BF),
    val secondary: Color = Color(0xFF061B3A),
    val disabledPrimary: Color = Color(0xFF0B1A3A),
    val disabledSecondary: Color = Color(0xFF0B1A3A),
)

internal data class BackgroundsColors(
    val primary: Color = Color(0xFF02122B),
    val secondary: Color = Color(0xFF061B3A),
    val tertiary: Color = Color(0xFF11284A),
    val success: Color = Color(0xFF042436),
    val alert: Color = Color(0xFF362B17),
    val error: Color = Color(0xFF2B1111),
    val neutral: Color = Color(0xFF061B3A),
    val disabled: Color = Color(0x800B1A3A),
    val states: BackgroundStateColors = BackgroundStateColors(),
)

internal data class BackgroundStateColors(
    val success: Color = Color(0xFF042436),
)

internal data class PrimaryColors(
    val accent1: Color = Color(0xFF042D9A),
    val accent2: Color = Color(0xFF0339C7),
    val accent3: Color = Color(0xFF2155DF),
    val accent4: Color = Color(0xFF4879FD),
)

internal data class TextColors(
    val primary: Color = Color(0xFFF0F4FC),
    val light: Color = Color(0xFFC9D6E8),
    val extraLight: Color = Color(0xFF8295AE),
    val button: TextButtonColors = TextButtonColors()
)

internal data class TextButtonColors(
    val dark: Color = Color(0xFF02122B),
    val light: Color = Color(0xFFF0F4FC),
    val disabled: Color = Color(0xFF718096),
)

internal data class BordersColors(
    val normal: Color = Color(0xFF1B3F73),
    val light: Color = Color(0xFF12284A),
)

internal data class ButtonBorderColors(
    val default: Color = Color(0xFF2155DF),
    val disabled: Color = Color(0x992155DF),
)

internal data class AlertsColors(
    val success: Color = Color(0xFF11C89C),
    val error: Color = Color(0xFFFF5C5C),
    val warning: Color = Color(0xFFFFC25C),
    val info: Color = Color(0xFF5CA7FF),
)

internal data class NeutralsColors(
    val n50: Color = Color(0xFFFFFFFF),
    val n100: Color = Color(0xFFEFF2F6),
    val n200: Color = Color(0xFFE5E7EB),
    val n300: Color = Color(0xFFD1D5DB),
    val n400: Color = Color(0xFF9CA3AF),
    val n500: Color = Color(0xFF6B7280),
    val n600: Color = Color(0xFF4B5563),
    val n700: Color = Color(0xFF374151),
    val n800: Color = Color(0xFF1F2A37),
    val n900: Color = Color(0xFF000000),
    val secondary: Color = Color(0xFF061B3A),
) {
    companion object{
        val Default = NeutralsColors()
    }
}

internal data class FillsColors(
    val tertiary: Color = Color(0x1F787880),
)

internal data class Vibrant(
    val primary: Color = Color(0xFFF0F4FC),
)

internal data class Gradients(
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