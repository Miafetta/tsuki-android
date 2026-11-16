package cafe.miafetta.tsuki.ui.glass

import androidx.compose.ui.unit.dp

// Geometry of the top bar background (TsukiGlass), below the action row bottom (status bar + 52dp). It follows the HyperOS 4
// theme store home page (measured 2026-11-14): no tint, the page sharp again 8dp below the chips. Pages without the game row
// clear 8dp below the action row. Both blurred paths end at the bottom edge of the glass, the clear line.
internal val TopGlassFade = 8.dp
internal val GameChipSlot = 52.dp
// GameChips: 4dp above a 38dp chip; the 10dp below it is the row's own padding.
internal val GameChipBottom = 42.dp
internal val GameHeaderFade = GameChipBottom + TopGlassFade

// MiGlass path, first choice: one rectangle radius over the whole bar and no tint. Calibrated on the device (2026-11-16,
// 3.25 px/dp): at radius 17 (miGlassRadiusPixels 78 px) the rectangle blurred with a full-strength sigma of 37 px, about
// 0.475 x the MiGlass pixel radius, against 28-31 px at the theme store's strongest. 14 gives 64 px, so a sigma of about
// 30 px. Its radius cannot vary down the bar, so the sampled result fades out instead (DstIn): on the home page from the
// action row bottom to the clear line, on other pages from NativeTopGlassFadeAbove above the action row bottom, where the
// small title's ink has ended, so no sharp content shows behind the title.
internal const val NativeTopGlassRadius = 14f
internal val NativeTopGlassFadeAbove = 16.dp

/** Where the MiGlass top bar starts to fade, as a fraction of its [height] px: [above] px above the [actionRow] bottom. */
internal fun nativeTopGlassFadeFrom(height: Float, actionRow: Float, above: Float): Float {
    val total = cleanLength(height)
    if (total <= 0f) return 0f
    return ((cleanLength(actionRow) - cleanLength(above)).coerceAtLeast(0f) / total).coerceIn(0f, 1f)
}

// miuix-blur progressiveBlur, the fallback where MiGlass is unavailable. On the device (2026-11-15) a 23.25dp radius, fitted to
// the theme store's sigma profile, flickered while scrolling (the blurred area changed 3-8 levels per pixel of scroll and
// stood still instead of moving with the content) and looked blocky near the clear line, both from its 8x downscaled layer.
// 10dp, the home fallback of 2.2.2, downscales less; this path can still flicker slightly while scrolling.
internal val TopGlassBlurRadius = 10.dp
internal const val TopGlassBlurCurve = .55f
// Full strength ends this far below the status bar, on every page.
internal val TopGlassPlateau = 6.dp
// Without progressiveGradient the clear end of progressiveBlur keeps the downscale's softness, so the sampled result fades
// out over the last 8dp and the page itself shows through sharp; the system cross-fades the same way.
internal val TopGlassClearFade = 8.dp

/**
 * Where the top glass changes, as fractions of its height: [fullUntil] is the ProgressiveBlur start (full strength above
 * it, zero strength at the bottom edge, the clear line), [fadeFrom] where the DstIn mask starts to fade the sampled result.
 */
internal data class TopGlassCurve(val fullUntil: Float, val fadeFrom: Float)

/**
 * Splits a top glass [height] px tall whose status bar is [statusBar] px: full strength down to [plateau] below the status
 * bar, the sampled result fading out over the last [fade]. Non-finite or negative inputs count as 0, so a glass that has not
 * been measured yet (height 0) gets a valid, empty curve.
 */
internal fun topGlassCurve(height: Float, statusBar: Float, plateau: Float, fade: Float): TopGlassCurve {
    val total = cleanLength(height)
    if (total <= 0f) return TopGlassCurve(0f, 0f)
    val full = ((cleanLength(statusBar) + cleanLength(plateau)) / total).coerceIn(0f, 1f)
    val fadeFrom = (1f - cleanLength(fade) / total).coerceIn(0f, 1f)
    return TopGlassCurve(full, fadeFrom)
}

private fun cleanLength(value: Float): Float = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
