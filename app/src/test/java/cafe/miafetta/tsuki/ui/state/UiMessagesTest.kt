package cafe.miafetta.tsuki.ui.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class UiMessagesTest {
    @Test fun repeatedReadFailureIsQuietUntilSuccessOrRoleChange() = runBlocking {
        val messages = UiMessages()
        val failures = ReadFailureMessages(messages)
        val role = UiRole("account", "账号", "role", "角色")
        repeat(3) { failures.send(role, "读取失败") }
        assertEquals(UiMessage("读取失败"), messages.events.first())
        assertNull(withTimeoutOrNull(25) { messages.events.first() })
        failures.clear() // Successful read.
        failures.send(role, "读取失败")
        assertEquals("读取失败", messages.events.first().text)
        failures.clear() // Switched role, including returning to this role later.
        failures.send(role.copy(id = "other"), "读取失败")
        assertEquals("读取失败", messages.events.first().text)
        assertNull(withTimeoutOrNull(25) { messages.events.first() })
    }

    @Test fun failureIsConsumedOnceAcrossCollectors() = runBlocking {
        val messages = UiMessages()
        messages.send("网络失败")
        assertEquals("网络失败", messages.events.first().text)
        // Recreating a collector after navigation or dismissal must not replay consumed state.
        repeat(3) { assertNull(withTimeoutOrNull(25) { messages.events.first() }) }
    }

    @Test fun eachNewFailureIsDeliveredEvenWithIdenticalText() = runBlocking {
        val messages = UiMessages()
        messages.send("网络失败")
        messages.send("网络失败")
        assertEquals(listOf("网络失败", "网络失败"), messages.events.take(2).map { it.text }.toList())
        assertNull(withTimeoutOrNull(25) { messages.events.first() })
    }

}
