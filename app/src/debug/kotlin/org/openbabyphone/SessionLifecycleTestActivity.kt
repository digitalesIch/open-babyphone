package org.openbabyphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.openbabyphone.ui.theme.QuietEngineTheme

object SessionLifecycleTestHost {
    var content: @Composable () -> Unit by mutableStateOf({})
}

class SessionLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuietEngineTheme { SessionLifecycleTestHost.content() }
        }
    }
}
