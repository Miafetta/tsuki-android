package cafe.miafetta.tsuki.core.reminder

// The role a reminder belongs to. Reminders are bound to roles, never to widget instances, so every widget showing the
// same role shares one rule and one trigger record. Endfield character IDs already encode the server
// ("<serverId>~<roleId>"); Arknights uses the numeric UID.
data class ReminderSubject(val providerId: String, val accountId: String, val characterId: String) {
    init {
        require(listOf(providerId, accountId, characterId).all { it.isNotBlank() && it.length <= 80 })
    }
}
