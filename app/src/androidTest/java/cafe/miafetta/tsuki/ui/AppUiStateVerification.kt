package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.state.AppState
import cafe.miafetta.tsuki.ui.state.UiNotifier

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.initializer
import cafe.miafetta.tsuki.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

internal fun AppUiVerification.verifyReadAndLifecycle(host: MainActivity) {
    val storage = cafe.miafetta.tsuki.StorageContract
    var response = "error"
    val notifications = CopyOnWriteArrayList<String>()
    val fixture = AppState(host.applicationContext, UiNotifier { notifications.add(it) }) { method, _ ->
        Bundle().apply {
            if (method == storage.OVERVIEW) putString(storage.JSON, """{"accounts":[],"widgets":[]}""")
            if (method == storage.DASHBOARD) when (response) {
                "error" -> putBoolean(storage.ERROR, true)
                "malformed" -> putString(storage.SNAPSHOT, "{")
            }
        }
    }
    runBlocking {
        repeat(3) { fixture.dashboard(role) }
        check(fixture.messages.events.first().text.isNotEmpty())
        check(withTimeoutOrNull(50) { fixture.messages.events.first() } == null)
        response = "success"
        fixture.dashboard(role)
        response = "malformed"
        repeat(3) { fixture.dashboard(role) }
        check(fixture.messages.events.first().text == "缓存数据已损坏，请刷新")
        check(withTimeoutOrNull(50) { fixture.messages.events.first() } == null)
        fixture.dashboard(role.copy(id = "other"))
        check(fixture.messages.events.first().text == "缓存数据已损坏，请刷新")
        fixture.dashboard(role)
        check(fixture.messages.events.first().text == "缓存数据已损坏，请刷新")
    }
    installState(host, fixture)
    frames(host)
    for (scenario in listOf("immediate", "showing", "stopped")) backgroundMessage(host, fixture, notifications, scenario)
    recreationMessages(host, fixture, notifications)
}

// Replaces the activity's AppState with the fixture and shows the production app on it.
private fun AppUiVerification.installState(host: MainActivity, fixture: AppState) = runOnMainSync {
    host.viewModelStore.clear()
    androidx.lifecycle.ViewModelProvider(host, androidx.lifecycle.viewmodel.viewModelFactory {
        initializer { fixture }
    })[AppState::class.java]
    host.setContent { TsukiApp(host, 0, 0) }
}

// A message sent before, while or after the activity stops is shown exactly once, and only after it returns.
private fun AppUiVerification.backgroundMessage(host: MainActivity, fixture: AppState, notifications: List<String>,
    scenario: String) {
    val text = "后台消息验证-$scenario"
    if (scenario == "showing") {
        runOnMainSync { fixture.messages.send(text) }
        framesUntil(host) { notifications.count { it == text } == 1 }
    }
    runOnMainSync {
        if (scenario == "immediate") fixture.messages.send(text)
        check(host.moveTaskToBack(true))
    }
    val deadline = System.currentTimeMillis() + 5000
    var stopped = false
    while (!stopped && System.currentTimeMillis() < deadline) {
        runOnMainSync { stopped = !host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        Thread.sleep(50)
    }
    check(stopped) { "Activity did not STOP" }
    val countAtStop = notifications.count { it == text }
    if (scenario == "stopped") runOnMainSync { fixture.messages.send(text) }
    Thread.sleep(5500)
    check(notifications.count { it == text } == countAtStop) { "Toast consumed while stopped" }
    shell("am start -n cafe.miafetta.tsuki/.MainActivity -f 0x10200000")
    framesUntil(host) { notifications.count { it == text } == 1 }
    check(hostRunner.uiActivity === host)
    frames(host)
    check(notifications.count { it == text } == 1) { "Message repeated after foreground" }
}

private fun AppUiVerification.recreationMessages(host: MainActivity, fixture: AppState, notifications: List<String>) {
    var current = host
    repeat(3) { index ->
        val text = "重建消息验证-$index"
        val previous = current
        runOnMainSync {
            fixture.messages.send(text)
            previous.recreate()
        }
        val deadline = System.currentTimeMillis() + 10000
        while (hostRunner.uiActivity === previous && System.currentTimeMillis() < deadline) Thread.sleep(50)
        current = checkNotNull(hostRunner.uiActivity)
        check(current !== previous)
        framesUntil(current) { notifications.count { it == text } == 1 }
        runOnMainSync { check(androidx.lifecycle.ViewModelProvider(current)[AppState::class.java] === fixture) }
        Thread.sleep(1000)
        check(notifications.count { it == text } == 1) { "Recreated message lost or replayed" }
    }
    runOnMainSync { current.finish() }
}

internal fun AppUiVerification.verifyErrorMessages(host: MainActivity, folder: File) {
    val storage = cafe.miafetta.tsuki.StorageContract
    var failed = false
    val loads = java.util.concurrent.atomic.AtomicInteger()
    val notifications = CopyOnWriteArrayList<String>()
    val fixture = AppState(host.applicationContext, UiNotifier { notifications.add(it) }) { method, _ ->
        Bundle().apply {
            when (method) {
                storage.OVERVIEW -> {
                    loads.incrementAndGet()
                    putString(storage.JSON, """{"accounts":[{"id":"error-fixture","name":"错误测试账号","roles":""" +
                        """[{"id":"1","label":"错误测试博士 · 官服"}]}],"widgets":[]}""")
                }
                storage.REFRESH_ROLE -> {
                    failed = true
                    putString(storage.FAILURE, "NETWORK")
                }
                storage.DASHBOARD -> if (failed) putString(storage.FAILURE, "NETWORK")
                storage.LOGOUT -> {
                    putBoolean(storage.ERROR, true)
                    putString(storage.FAILURE, "NETWORK")
                }
            }
        }
    }
    installState(host, fixture)
    frames(host)
    // The initial automatic refresh uses the same controlled failure as the manual refresh.
    framesUntil(host) { notifications.size == 1 }
    capture(folder, "toast-error-inline-status")
    check(notifications.size == 1)
    click("我的")
    frames(host)
    click("概览")
    frames(host)
    check(notifications.size == 1) { "Cached failure replayed after navigation" }
    val beforeLoads = loads.get()
    click("部件")
    frames(host)
    check(loads.get() == beforeLoads + 1) { "Widgets visit loaded ${loads.get() - beforeLoads} times" }
    val beforeData = fixture.data
    val beforeKey = fixture.dataKey
    runBlocking { fixture.previewSnapshot(role) }
    check(fixture.data === beforeData && fixture.dataKey == beforeKey) { "Preview changed overview data" }
    click("添加")
    frames(host)
    click("关闭")
    frames(host)
    click("概览")
    frames(host)
    check(notifications.size == 1) { "Cached failure replayed after sheet dismissal" }
    capture(folder, "error-after-navigation")
    click("我的")
    frames(host)
    click("账号菜单")
    frames(host)
    click("删除账号")
    frames(host)
    click("删除")
    frames(host)
    check(fixture.error.isNotEmpty()) { "Confirmation failure was not inline" }
    check(notifications.size == 1)
    click("取消")
    frames(host)
    check(fixture.error.isEmpty()) { "Dismissal retained inline error" }
    click("概览")
    frames(host)
    check(notifications.size == 1)
    click("刷新")
    frames(host)
    check(notifications.size == 2) { "A new refresh failure did not emit a message" }
}
