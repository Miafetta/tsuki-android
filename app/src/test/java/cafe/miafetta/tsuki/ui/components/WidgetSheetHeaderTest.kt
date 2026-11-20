package cafe.miafetta.tsuki.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSheetHeaderTest {
    // HyperOS's add sheet: back, the app instead of a title, and 添加到桌面; its edit sheet: close, 编辑小部件 and 应用.
    @Test fun headsNameTheirButtonsLikeTheSystemSheets() {
        val new = WidgetSheetHeader.New("arknights", "明日方舟数据工具", "查看明日方舟的理智、任务与基建进度")
        assertNull(new.title)
        assertEquals("返回", new.dismissLabel)
        assertEquals("添加到桌面", new.confirmLabel)
        assertEquals("编辑小部件", WidgetSheetHeader.Edit.title)
        assertEquals("关闭", WidgetSheetHeader.Edit.dismissLabel)
        assertEquals("应用", WidgetSheetHeader.Edit.confirmLabel)
        for (header in listOf(new, WidgetSheetHeader.Edit))
            for (text in listOfNotNull(header.title, header.dismissLabel, header.confirmLabel)) assertFalse(text, "·" in text)
    }
}
