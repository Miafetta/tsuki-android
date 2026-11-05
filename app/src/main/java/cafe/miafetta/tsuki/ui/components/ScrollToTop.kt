package cafe.miafetta.tsuki.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.children
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior

internal interface ScrollToTopTarget {
    fun canScrollUp(): Boolean
    fun scrollToTop()
}

// HyperOS 4 scrolls to the top when the status bar is tapped. In the focused window it looks for an on-screen
// ViewGroup with canScrollVertically(-1) and a positive computeVerticalScrollOffset(), then calls its
// smoothScrollToPosition(int) by reflection (scrollToPosition(int) for long distances), with 0. Compose lists are not
// Views and report no offset, so every Tsuki window carries this empty host (see setTsukiContent): it reports the page
// that is shown and forwards the call to it. Pages register through ScrollToTopEffect; the last active one wins. With
// no target the host does not qualify, so the system handles native scrollers such as the web login WebView itself.
// Elsewhere nothing calls these methods and the host is an empty, transparent FrameLayout.
internal class ScrollToTopHost(context: Context) : FrameLayout(context) {
    private val targets = ArrayList<ScrollToTopTarget>()
    private val target get() = targets.lastOrNull()

    fun add(target: ScrollToTopTarget) {
        targets.add(target)
    }

    fun remove(target: ScrollToTopTarget) {
        targets.remove(target)
    }

    override fun canScrollVertically(direction: Int): Boolean =
        (direction < 0 && target?.canScrollUp() == true) || super.canScrollVertically(direction)

    // Only "away from the top" matters to the system; it skips views that report 0.
    override fun computeVerticalScrollOffset(): Int = if (target?.canScrollUp() == true) 1 else 0

    @Keep
    fun smoothScrollToPosition(position: Int) {
        if (position == 0) target?.scrollToTop()
    }

    @Keep
    fun scrollToPosition(position: Int) {
        if (position == 0) target?.scrollToTop()
    }
}

// Replaces ComponentActivity.setContent for Tsuki windows. The ComposeView stays the first content child, exactly as
// setContent leaves it (state restoration and setContent reuse depend on that); the host is an empty sibling above it.
// The system walks the whole window, so the host does not need to wrap the content; as a sibling it never sits between
// the content and its touches or accessibility nodes: it has no children, draws nothing, ignores touches and is hidden
// from accessibility.
internal fun ComponentActivity.setTsukiContent(content: @Composable () -> Unit) {
    val match = ViewGroup.LayoutParams.MATCH_PARENT
    setContentView(ComposeView(this).apply { setContent(content) }, ViewGroup.LayoutParams(match, match))
    addContentView(ScrollToTopHost(this).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }, ViewGroup.LayoutParams(match, match))
}

// Brings a collapsed large title back after a scroll to top; shared with the overview's top refresh.
internal suspend fun expandTopBar(behavior: ScrollBehavior) {
    val header = behavior.state
    animate(header.heightOffset, 0f, animationSpec = behavior.snapAnimationSpec ?: spring()) { value, _ ->
        header.heightOffset = value
    }
    header.contentOffset = 0f
}

// Registers the visible page's scroller with the window's ScrollToTopHost while enabled (the page is the active one).
@Composable
internal fun ScrollToTopEffect(enabled: Boolean, canScrollUp: () -> Boolean, scrollToTop: suspend () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentCanScrollUp by rememberUpdatedState(canScrollUp)
    val currentScrollToTop by rememberUpdatedState(scrollToTop)
    DisposableEffect(view, enabled) {
        val host = view.rootView.findViewById<ViewGroup>(android.R.id.content)?.children
            ?.filterIsInstance<ScrollToTopHost>()?.firstOrNull()
        if (host == null || !enabled) return@DisposableEffect onDispose {}
        val target = object : ScrollToTopTarget {
            override fun canScrollUp() = currentCanScrollUp()
            override fun scrollToTop() {
                scope.launch { currentScrollToTop() }
            }
        }
        host.add(target)
        onDispose { host.remove(target) }
    }
}
