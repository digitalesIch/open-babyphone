package org.openbabyphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import org.openbabyphone.ui.theme.QuietEngineTheme

object SessionLifecycleTestHost {
    var content: @Composable () -> Unit = {}
}

class SessionLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuietEngineTheme { SessionLifecycleTestHost.content() }
        }
    }
}
