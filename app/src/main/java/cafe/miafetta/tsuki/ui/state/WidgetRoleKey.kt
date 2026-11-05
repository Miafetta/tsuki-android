package cafe.miafetta.tsuki.ui.state

// Role key the overview should show after a tap on desktop widget [widgetId]. Null when the widget is unknown, not yet
// bound, or bound to a role that is no longer listed (account or role removed); callers then keep the current selection
// instead of pointing it at a role the overview cannot show.
internal fun widgetRoleKey(widgets: List<UiWidget>, roles: List<UiRole>, widgetId: Int): String? {
    val widget = widgets.firstOrNull { it.id == widgetId } ?: return null
    if (widget.accountId.isEmpty() || widget.roleId.isEmpty()) return null
    return roles.firstOrNull { it.accountId == widget.accountId && it.id == widget.roleId }?.key
}
