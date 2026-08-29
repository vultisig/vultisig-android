package com.vultisig.wallet.ui.screens.v2.home.pager.banner

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.PromoBanner
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

/**
 * The promos the home carousel can show, in the order Figma renders them ("Banners NEW 2026", node
 * 68734:97320). Declaration order *is* carousel order, which is why Kamino leads.
 *
 * Each case owns only its identity and the dismissal record it writes ([promoBanner]); everything
 * visual is looked up in [spec], and every eligibility rule lives in the ViewModel. The split
 * matters because the dismissal id is storage — renaming a case must never orphan what a user has
 * already closed.
 */
internal enum class HomeBannerType(val promoBanner: PromoBanner) {
    KaminoEarn(PromoBanner.KaminoEarnSolana),
    UpgradeVault(PromoBanner.UpgradeVaultDkls),
    RujiraStaking(PromoBanner.RujiraStakingThorchain),
    FollowX(PromoBanner.FollowXVultisig),
    BackupVault(PromoBanner.BackupVaultShare),
    ReferralRewards(PromoBanner.ReferralRewardsCode),
    BuyVult(PromoBanner.BuyVultSwap),
}

/**
 * Where a banner's 3D artwork sits and how much of it is visible.
 *
 * Figma draws the art into a fixed square window pinned to the banner's trailing edge and clips
 * whatever falls outside, then scales and nudges the source inside that window per banner. Those
 * three numbers are what differs between the seven banners, so they are carried as data rather than
 * restated in seven layouts. [scale] and the offsets are read straight off the Figma percentages:
 * `size-[115.6%]` is `scale = 1.156f`, `left-[-7.4%]` on a 125 window is `(-9.25).dp`.
 */
@Immutable
private data class BannerArt(
    @param:DrawableRes val image: Int,
    val windowSize: Dp = 125.dp,
    /**
     * Gap between the art window and the banner's trailing edge (Figma `x` read from the right).
     */
    val endInset: Dp = 0.dp,
    val topInset: Dp = 5.dp,
    val scale: Float = 1f,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
)

@Immutable
private data class BannerSpec(
    @param:StringRes val caption: Int,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val iconSize: Dp,
    val art: BannerArt,
    val gradientEnd: Color,
)

// Trailing gradient tints, one per banner. Not theme tokens: they are per-promo brand accents that
// exist nowhere else in the app, and are shared with iOS as literals for exactly that reason.
private val PromoBannerBlue = Color(0xFF0348BB)
private val PromoBannerPurple = Color(0xFFA623EB)
private val PromoBannerIndigo = Color(0xFF1D0F88)
private val PromoBannerMutedPurple = Color(0xFF5C5277)
private val PromoBannerDeepBlue = Color(0xFF07156F)
private val PromoBannerBrightBlue = Color(0xFF0343CD)

/** Figma's banner height: 20 of clearance above and below a 41 dp icon tile. Fixed, like iOS. */
private val BannerHeight = 81.dp

/** Room kept clear on the trailing side so long copy wraps before it reaches the close button. */
private val CloseButtonReserve = 32.dp

/**
 * Figma puts a 2 px layer blur on the artwork, but that number is a Figma blur amount, not a
 * Gaussian sigma: fitting the rendered node against the source asset puts the actual spread at
 * about half a dp. Compose's radius maps to a noticeably larger sigma than either, so passing 2.dp
 * here turns the render to mush — this is the radius that reproduces Figma's spread.
 */
private val ArtBlurRadius = 0.5.dp

/** Figma "Glass close": a 40 dp disc inset 10 dp from the card's top-trailing corner. */
private val GlassCloseSize = 40.dp
private val GlassCloseInset = 10.dp
private val GlassCloseIconSize = 16.dp
private val GlassCloseBlur = 20.dp

/**
 * The gradient's first stop. Everything left of the banner's midpoint is flat surface, and only the
 * trailing half carries the promo's tint.
 */
private const val GRADIENT_START_STOP = 0.5f

private const val GRADIENT_ALPHA = 0.69f

private fun HomeBannerType.spec(): BannerSpec =
    when (this) {
        HomeBannerType.KaminoEarn ->
            BannerSpec(
                caption = R.string.kamino_banner_caption,
                title = R.string.kamino_banner_title,
                icon = R.drawable.banner_icon_kamino,
                iconSize = 19.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_kamino,
                        scale = 0.856f,
                        offsetX = 11.5.dp,
                        offsetY = 6.dp,
                    ),
                gradientEnd = PromoBannerDeepBlue,
            )
        HomeBannerType.UpgradeVault ->
            BannerSpec(
                caption = R.string.upgrade_banner_sign_faster,
                title = R.string.upgrade_banner_upgrade_your,
                icon = R.drawable.banner_icon_upgrade,
                iconSize = 20.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_upgrade,
                        offsetX = (-9.25).dp,
                        scale = 1.2f,
                    ),
                gradientEnd = PromoBannerBlue,
            )
        HomeBannerType.RujiraStaking ->
            BannerSpec(
                caption = R.string.rujira_banner_caption,
                title = R.string.rujira_banner_title,
                icon = R.drawable.ruji,
                iconSize = 20.dp,
                art = BannerArt(image = R.drawable.banner_art_rujira, scale = 0.9f, offsetY = 8.dp),
                gradientEnd = PromoBannerPurple,
            )
        HomeBannerType.FollowX ->
            BannerSpec(
                caption = R.string.invite_to_x_banner_title,
                title = R.string.invite_to_x_banner_desc,
                icon = R.drawable.banner_icon_follow_x,
                iconSize = 20.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_follow_x,
                        scale = 0.896f,
                        offsetX = 6.5.dp,
                        offsetY = 14.dp,
                    ),
                gradientEnd = PromoBannerIndigo,
            )
        HomeBannerType.BackupVault ->
            BannerSpec(
                caption = R.string.backup_banner_caption,
                title = R.string.backup_banner_title,
                icon = R.drawable.banner_icon_backup,
                iconSize = 20.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_backup,
                        scale = 0.844f,
                        offsetX = 2.75.dp,
                        offsetY = 5.5.dp,
                    ),
                gradientEnd = PromoBannerMutedPurple,
            )
        HomeBannerType.ReferralRewards ->
            BannerSpec(
                caption = R.string.referral_rewards_banner_caption,
                title = R.string.referral_rewards_banner_title,
                icon = R.drawable.banner_icon_referral,
                iconSize = 20.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_referral,
                        scale = 1.104f,
                        offsetX = (-6).dp,
                        offsetY = 8.dp,
                    ),
                gradientEnd = PromoBannerDeepBlue,
            )
        HomeBannerType.BuyVult ->
            BannerSpec(
                caption = R.string.buy_vult_banner_title,
                title = R.string.buy_vult_banner_desc,
                icon = R.drawable.banner_icon_buy_vult,
                iconSize = 19.dp,
                art =
                    BannerArt(
                        image = R.drawable.banner_art_buy_vult,
                        windowSize = 112.dp,
                        endInset = 6.5.dp,
                    ),
                gradientEnd = PromoBannerBrightBlue,
            )
    }

/**
 * One page of the home promo carousel.
 *
 * The whole card is the tap target — Figma dropped the separate CTA button the older banners had —
 * so the only competing hit region is the close button, drawn after the card so it wins its own.
 */
@Composable
internal fun HomeBanner(
    banner: HomeBannerType,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = banner.spec()
    val backdrop = rememberGraphicsLayer()
    val frost = rememberGraphicsLayer()
    val glassTint = Theme.v2.colors.neutrals.n50

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(BannerHeight)
                    .clip(Theme.v2.radius.xl)
                    .background(Theme.v2.colors.backgrounds.surface1)
                    .background(
                        Brush.horizontalGradient(
                            GRADIENT_START_STOP to
                                Theme.v2.colors.backgrounds.surface1.copy(alpha = GRADIENT_ALPHA),
                            1f to spec.gradientEnd.copy(alpha = GRADIENT_ALPHA),
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = Theme.v2.radius.xl,
                    )
                    .clickOnce(onClick = onClick)
        ) {
            // matchParentSize keeps the decorative layer out of the banner's measurement, so the
            // 125 dp render inside it cannot stretch an 81 dp card to its own height. Its drawing
            // is also recorded, because the close control frosts whatever it sits on.
            Box(
                modifier =
                    Modifier.matchParentSize().drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    }
            ) {
                BannerArtwork(art = spec.art, modifier = Modifier.align(Alignment.TopEnd))
            }

            Box(
                modifier =
                    Modifier.matchParentSize().drawBehind {
                        drawGlassCloseBackdrop(backdrop = backdrop, frost = frost, tint = glassTint)
                    }
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(41.dp)
                            .clip(Theme.v2.radius.lg)
                            .background(Theme.v2.colors.backgrounds.tertiary_2)
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.variables.bordersExtraLight,
                                shape = Theme.v2.radius.lg,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = spec.icon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(spec.iconSize),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = CloseButtonReserve),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(spec.caption),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(spec.title),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // The icon carries no description of its own, so the control announces once, as a button.
        val dismissLabel = stringResource(R.string.close_sheet_content_description)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(all = GlassCloseInset)
                    .size(GlassCloseSize)
                    .clip(Theme.v2.radius.pill)
                    .clickOnce(onClick = onCloseClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = dismissLabel
                    },
        ) {
            UiIcon(
                drawableResId = R.drawable.glass,
                tint = Theme.v2.colors.neutrals.n100,
                size = GlassCloseIconSize,
            )
        }
    }
}

/**
 * Frosts the banner under the close control: what the card drew is re-drawn blurred and clipped to
 * the control's circle, so the artwork behind it reads as glass rather than as a bare icon. Figma's
 * fill alone is white at 1 %, which is invisible on its own — the blur is what makes the disc.
 *
 * [backdrop] holds the artwork layer, recorded a moment earlier in the same frame; [frost] carries
 * the blur, since a render effect belongs to a layer rather than to a draw call. The blur needs API
 * 31 — below it the layer draws unblurred, leaving a plain 1 % tint.
 */
private fun DrawScope.drawGlassCloseBackdrop(
    backdrop: GraphicsLayer,
    frost: GraphicsLayer,
    tint: Color,
) {
    val blurRadius = GlassCloseBlur.toPx()
    frost.renderEffect = BlurEffect(radiusX = blurRadius, radiusY = blurRadius)
    frost.record { drawLayer(backdrop) }

    val radius = GlassCloseSize.toPx() / 2f
    val center =
        Offset(
            x = size.width - GlassCloseInset.toPx() - radius,
            y = GlassCloseInset.toPx() + radius,
        )

    clipPath(Path().apply { addOval(Rect(center = center, radius = radius)) }) {
        drawLayer(frost)
        drawCircle(color = tint.copy(alpha = 0.01f), radius = radius, center = center)
    }
}

/**
 * The decorative 3D render, pinned to the banner's trailing edge.
 *
 * Both sizes are `requiredSize`, not `size`: the window is taller than the banner and the source is
 * often larger than the window, so honouring the incoming constraints would shrink each of them
 * back to its container and silently lose Figma's crop.
 */
@Composable
private fun BannerArtwork(art: BannerArt, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .offset(x = -art.endInset, y = art.topInset)
                .requiredSize(art.windowSize)
                .clipToBounds()
    ) {
        Image(
            painter = painterResource(id = art.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.requiredSize(art.scale * art.windowSize)
                    .offset(x = art.offsetX, y = art.offsetY)
                    .blur(ArtBlurRadius),
        )
    }
}

@Preview
@Composable
private fun HomeBannerPreview() {
    Column(
        modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeBannerType.entries.forEach { banner ->
            HomeBanner(banner = banner, onClick = {}, onCloseClick = {})
        }
    }
}
