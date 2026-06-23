package org.openbabyphone

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun StartScreen(
    onNavigateToMonitor: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isExpanded = LocalWindowWidthSizeClass.current == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded

    var childPermissionDenied by remember { mutableStateOf(false) }
    var parentPermissionDenied by remember { mutableStateOf(false) }

    val childPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val parentPermissions = remember {
        buildList {
            add(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val childPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = childPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            childPermissionDenied = false
            onNavigateToMonitor()
        } else {
            childPermissionDenied = true
        }
    }

    val parentPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = parentPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            parentPermissionDenied = false
            onNavigateToDiscover()
        } else {
            parentPermissionDenied = true
        }
    }

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
                    val allGranted = childPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(
                            context, perm
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        childPermissionDenied = false
                        onNavigateToMonitor()
                    } else {
                        childPermissionLauncher.launch(childPermissions)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_device_button")
            ) {
                Text(stringResource(R.string.useAsChildDevice))
            }

            if (childPermissionDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_rationale_child),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
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
                    val allGranted = parentPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(
                            context, perm
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        parentPermissionDenied = false
                        onNavigateToDiscover()
                    } else {
                        parentPermissionLauncher.launch(parentPermissions)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_device_button")
            ) {
                Text(stringResource(R.string.useAsParentDevice))
            }

            if (parentPermissionDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_rationale_parent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
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