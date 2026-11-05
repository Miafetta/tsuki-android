package cafe.miafetta.tsuki.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

// Miuix TopAppBar pads itself with systemBars (status bar plus any caption bar); page layout must use the same top inset.
@Composable
internal fun topBarInset(): Dp = WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

@Composable
internal fun bottomBarInset(): Dp = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
