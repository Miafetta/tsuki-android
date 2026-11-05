package cafe.miafetta.tsuki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import cafe.miafetta.tsuki.ui.TsukiApp

class SklandAccountActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TsukiApp(this, initialPage = 2, resumeTick = resumeTick.intValue) }
    }
    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}
