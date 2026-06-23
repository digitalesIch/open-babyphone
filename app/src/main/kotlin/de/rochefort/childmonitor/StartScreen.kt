package de.rochefort.childmonitor

import android.Manifest
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.rochefort.childmonitor.Constants.PERMISSIONS_REQUEST_MULTICAST
import de.rochefort.childmonitor.Constants.PERMISSIONS_REQUEST_RECORD_AUDIO

@Composable
fun StartScreen(
    onNavigateToMonitor: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExpanded = LocalWindowWidthSizeClass.current == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isExpanded) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .then(if (isExpanded) Modifier.weight(1f) else Modifier.fillMaxWidth()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        onNavigateToMonitor()
                    } else {
                        ActivityCompat.requestPermissions(
                            context as android.app.Activity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            PERMISSIONS_REQUEST_RECORD_AUDIO
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_device_button")
            ) {
                Text(stringResource(R.string.useAsChildDevice))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.childDescription),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        onNavigateToDiscover()
                    } else {
                        ActivityCompat.requestPermissions(
                            context as android.app.Activity,
                            arrayOf(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE),
                            PERMISSIONS_REQUEST_MULTICAST
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_device_button")
            ) {
                Text(stringResource(R.string.useAsParentDevice))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.parentDescription),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}