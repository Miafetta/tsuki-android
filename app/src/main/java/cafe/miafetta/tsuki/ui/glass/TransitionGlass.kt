package cafe.miafetta.tsuki.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope

// Whether a page's glass is on, apart from whether the page takes input. While a page slides in or out its glass stays
// on, so the top bar and the dock keep their blur through the transition like the system apps; only a page that a
// finished transition has covered turns its glass off, since it is not drawn. Before 2.3.0 the glass of both pages
// turned off for the whole transition. Debug builds can bring that back at launch (MainActivity, extra
// "transitionGlass" = "inactive") to compare the two on a device; release builds always keep the glass.
internal object TransitionGlass {
    @Volatile
    var keep = true

    fun active(running: Boolean, relativeDepth: Float) = if (running) keep else relativeDepth == 0f
}

/** Reads transition settled-top state through [derivedStateOf] so frames mid-transition never recompose the page. */
@Composable
internal fun rememberNavActive(): Boolean {
    val transition = LocalNavTransitionScope.current
    return remember(transition) { derivedStateOf { !transition.isRunning && transition.relativeDepth == 0f } }.value
}

/** Reads [TransitionGlass.active] through [derivedStateOf] so glass toggles only at transition boundaries. */
@Composable
internal fun rememberNavGlassActive(): Boolean {
    val transition = LocalNavTransitionScope.current
    return remember(transition) {
        derivedStateOf { TransitionGlass.active(transition.isRunning, transition.relativeDepth) }
    }.value
}
