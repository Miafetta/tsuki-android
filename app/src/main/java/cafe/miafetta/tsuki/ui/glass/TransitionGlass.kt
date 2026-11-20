package cafe.miafetta.tsuki.ui.glass

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
