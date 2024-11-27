package com.vultisig.wallet.ui.theme

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
) {
    companion object {
        val Default = Colors()
    }
}