package cafe.miafetta.tsuki.ui.components

import kotlin.math.ceil

// How the top bar titles hand over while the bar collapses (TsukiTopBar, docs/UI_DESIGN_CONVENTIONS.md C1). Fitted frame
// by frame to a HyperOS 4 Settings recording (2026-11-15, 3.25 px/dp). The large title depends on the collapsed distance
// alone (fast, slow and reverse scrolls fall on one curve); the small title runs a timed 0..1 progress once the bar
// crosses a threshold, so it does not follow the finger. Distances and blur sigmas are in dp.
internal object TitleMotion {
    // The drawn large title sits this far below Miuix's. On the same phone (2026-11-15) the HyperOS 4 Settings large title
    // sits lower than Tsuki's. At 30sp (TsukiTextStyles) the ink, scaled from the 32sp reading, needs 26.75 px at
    // 3.25 px/dp to centre on the system's (rows 354..445), so 8.2dp. A fixed offset: the bar height, heightOffsetLimit and
    // the collapse curves below are unchanged. The system's first content starts lower still (row 509, 2026-11-16), so the
    // pages keep TopBarSpacing.ContentGapDp of their own padding below the bar instead of a taller bar.
    const val LargeTitleDropDp = 8.2f
    // The large title fades out linearly over this distance.
    const val LargeTitleFadeDp = 17.2f
    // The small title shows at and beyond this distance and hides below it, with no hysteresis: about 2.3 dp past the
    // point where the large title is gone, so for a short stretch neither title is visible.
    const val SmallTitleThresholdDp = 19.5f
    // The small title rises into place from this far below.
    const val SmallTitleRiseDp = 5.5f
    // Critically damped folmeSpring responses in seconds: showing settles in about 200 ms (half way at about 65 ms),
    // hiding in about 90 ms.
    const val SmallTitleShowResponse = .26f
    const val SmallTitleHideResponse = .15f

    // Gaussian sigma of the large title over the collapsed distance: none up to 1.5 dp, flat after the fade.
    val LargeTitleBlurAt = floatArrayOf(1.5f, 5f, 8.6f, 12.6f, LargeTitleFadeDp)
    val LargeTitleBlurSigma = floatArrayOf(0f, .46f, 1.54f, 2.15f, 2.3f)
    // Gaussian sigma of the small title over its progress: flat up to 0.4, sharp when settled.
    val SmallTitleBlurAt = floatArrayOf(.4f, .55f, .65f, .76f, .9f, 1f)
    val SmallTitleBlurSigma = floatArrayOf(1.85f, 1.4f, .8f, .31f, .15f, 0f)

    // Three sigma out a Gaussian has fallen to about 1 % of its peak; blurred layers keep that much transparent margin
    // around the text, or the blur is cut into a hard edge at the text bounds.
    const val BlurSpread = 3f

    // AOSP libs/hwui/utils/Blur.cpp: RenderEffect.createBlurEffect(radius) blurs with
    // sigma = BLUR_SIGMA_SCALE * radius + 0.5 (pixels, radius > 0), BLUR_SIGMA_SCALE = 0.57735 (1 / sqrt(3)).
    const val BlurSigmaScale = .57735f
    const val BlurSigmaOffsetPx = .5f
    // Same order as the menu content blur (TsukiMenu): smaller radii are no effect at all.
    const val MinBlurRadiusPx = .1f
}

private fun distance(collapsedDp: Float): Float = if (collapsedDp.isNaN()) 0f else collapsedDp.coerceAtLeast(0f)

private fun progress(p: Float): Float = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)

// Piecewise linear through (xs, ys), flat outside the first and last knot. xs must be increasing.
private fun piecewise(x: Float, xs: FloatArray, ys: FloatArray): Float {
    if (x <= xs.first()) return ys.first()
    if (x >= xs.last()) return ys.last()
    val i = xs.indexOfFirst { it >= x } - 1
    return ys[i] + (ys[i + 1] - ys[i]) * (x - xs[i]) / (xs[i + 1] - xs[i])
}

// Top of the large title layer in px: Miuix's title top (the collapsed height down), the fixed drop, the layer's bleed taken
// back, and the height offset 1:1 (Math.round is Miuix's fastRoundToInt; NaN rounds to 0), so the drop never changes while
// the bar collapses.
internal fun largeTitleTop(collapsedPx: Int, dropPx: Int, bleedPx: Int, heightOffset: Float): Int =
    collapsedPx + dropPx - bleedPx + Math.round(heightOffset)

internal fun largeTitleAlpha(collapsedDp: Float): Float = (1f - distance(collapsedDp) / TitleMotion.LargeTitleFadeDp).coerceIn(0f, 1f)

internal fun largeTitleBlurSigma(collapsedDp: Float): Float =
    piecewise(distance(collapsedDp), TitleMotion.LargeTitleBlurAt, TitleMotion.LargeTitleBlurSigma)

internal fun smallTitleVisible(collapsedDp: Float): Boolean = distance(collapsedDp) >= TitleMotion.SmallTitleThresholdDp

internal fun smallTitleAlpha(p: Float): Float = progress(p)

internal fun smallTitleShift(p: Float): Float = (1f - progress(p)) * TitleMotion.SmallTitleRiseDp

internal fun smallTitleBlurSigma(p: Float): Float =
    piecewise(progress(p), TitleMotion.SmallTitleBlurAt, TitleMotion.SmallTitleBlurSigma)

// Transparent margin, in whole dp, that holds the widest blur of either title.
internal fun titleBlurMarginDp(): Float =
    ceil(TitleMotion.BlurSpread * maxOf(TitleMotion.LargeTitleBlurSigma.max(), TitleMotion.SmallTitleBlurSigma.max()))

// Inverse of the AOSP radius-to-sigma conversion (Blur::convertSigmaToRadius): 0 when sigma cannot be reached.
internal fun blurRadiusForSigma(sigmaPx: Float): Float =
    if (sigmaPx.isNaN() || sigmaPx <= TitleMotion.BlurSigmaOffsetPx) 0f
    else (sigmaPx - TitleMotion.BlurSigmaOffsetPx) / TitleMotion.BlurSigmaScale

// The createBlurEffect radius for a title layer, or 0 for no render effect: a layer that draws nothing, or would blur by
// less than the menu threshold, carries none, so both resting states have no effect at all.
internal fun titleBlurRadius(sigmaPx: Float, alpha: Float): Float {
    val radius = blurRadiusForSigma(sigmaPx)
    return if (alpha > 0f && radius > TitleMotion.MinBlurRadiusPx && radius.isFinite()) radius else 0f
}
