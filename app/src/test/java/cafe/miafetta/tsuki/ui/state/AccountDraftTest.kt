package cafe.miafetta.tsuki.ui.state

import org.junit.Assert.*
import org.junit.Test

// The Skland nickname fills the optional account name only while it is blank, and a failed read changes nothing.
class AccountDraftTest {
    private val sample = "test-credential-0001"

    @Test fun lookupStartsOnlyForAValidCredWhileTheNameIsBlank() {
        val draft = AccountDraft()
        assertNull(draft.nameLookupCred())
        draft.cred = "short"
        assertNull(draft.nameLookupCred())
        draft.cred = "  $sample\n"
        assertEquals(sample, draft.nameLookupCred())
        draft.name = "我的账号"
        assertNull(draft.nameLookupCred())
        // An account opened from "更新 Cred" keeps its prefilled name.
        val updating = AccountDraft("已有名称", "account-1").apply { cred = sample }
        assertNull(updating.nameLookupCred())
    }

    @Test fun nicknameFillsABlankNameButNeverReplacesOne() {
        val draft = AccountDraft().apply { cred = sample }
        assertTrue(draft.offerName(sample, " 测试博士#0001 "))
        assertEquals("测试博士#0001", draft.name)
        assertFalse(draft.offerName(sample, "测试管理员#0002"))
        assertEquals("测试博士#0001", draft.name)
        val typed = AccountDraft("用户填写", null).apply { cred = sample }
        assertFalse(typed.offerName(sample, "测试博士#0001"))
        assertEquals("用户填写", typed.name)
        // Typed while the read was running.
        val late = AccountDraft().apply { cred = sample }
        late.name = "后来填写"
        assertFalse(late.offerName(sample, "测试博士#0001"))
        assertEquals("后来填写", late.name)
    }

    @Test fun staleOrFailedReadsLeaveTheNameBlankAndTheDraftUsable() {
        val draft = AccountDraft().apply { cred = sample }
        assertFalse(draft.offerName(sample, null))
        assertFalse(draft.offerName(sample, "   "))
        assertEquals("", draft.name)
        assertEquals(sample, draft.cred)
        // The Cred changed, or the editor was closed, before the nickname arrived.
        draft.cred = "test-credential-0002"
        assertFalse(draft.offerName(sample, "测试博士#0001"))
        draft.clear()
        assertFalse(draft.offerName(sample, "测试博士#0001"))
        assertEquals("", draft.name)
        val long = AccountDraft().apply { cred = sample }
        assertTrue(long.offerName(sample, "测".repeat(AccountDraft.NAME_LIMIT + 3)))
        assertEquals(AccountDraft.NAME_LIMIT, long.name.length)
    }
}
