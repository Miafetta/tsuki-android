package cafe.miafetta.tsuki.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseReflowTest {
    @Test fun joinsWrappedLinesAndKeepsParagraphs() {
        val source = "The MIT License (MIT)\r\n\r\nPermission is hereby granted,\r\n  free of charge, to any person\r\n\r\n\r\n" +
            "THE SOFTWARE IS PROVIDED\n\"AS IS\"\n"
        val expected = "The MIT License (MIT)\n\nPermission is hereby granted, free of charge, to any person\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\""
        assertEquals(expected, reflowLicense(source))
    }
}
