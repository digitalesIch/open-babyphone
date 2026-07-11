package org.openbabyphone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.openbabyphone.ui.theme.Spacing

@Composable
fun StartScreen(
    onNavigateToMonitor: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current

    var childPermissionDenied by remember { mutableStateOf(false) }
    var parentPermissionDenied by remember { mutableStateOf(false) }
    var childPermissionPermanentlyDenied by remember { mutableStateOf(false) }
    var parentPermissionPermanentlyDenied by remember { mutableStateOf(false) }

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
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
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
            childPermissionPermanentlyDenied = false
            onNavigateToMonitor()
        } else {
            childPermissionDenied = true
            val shouldShowRationale = childPermissions.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context as android.app.Activity, perm
                )
            }
            childPermissionPermanentlyDenied = !shouldShowRationale
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
            parentPermissionPermanentlyDenied = false
            onNavigateToDiscover()
        } else {
            parentPermissionDenied = true
            val shouldShowRationale = parentPermissions.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context as android.app.Activity, perm
                )
            }
            parentPermissionPermanentlyDenied = !shouldShowRationale
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(Spacing.space16)
                .verticalScroll(rememberScrollState())
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark(size = 64.dp)

            Spacer(modifier = Modifier.height(Spacing.space16))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W900),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.space8))

            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.space16)
            )

            Spacer(modifier = Modifier.height(Spacing.space32))

            OdPrimaryButton(
                text = stringResource(R.string.use_as_child_device),
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
                modifier = Modifier.testTag("child_device_button")
            )

            if (childPermissionDenied) {
                Spacer(modifier = Modifier.height(Spacing.space8))
                Text(
                    text = if (childPermissionPermanentlyDenied) {
                        stringResource(R.string.permission_permanently_denied_child)
                    } else {
                        stringResource(R.string.permission_rationale_child)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                if (childPermissionPermanentlyDenied) {
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    OdTextButton(
                        text = stringResource(R.string.open_app_settings),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("child_open_settings_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.space8))

            Text(
                text = stringResource(R.string.child_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.space16)
            )

            Spacer(modifier = Modifier.height(Spacing.space32))

            OdOutlinedActionButton(
                text = stringResource(R.string.use_as_parent_device),
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
                modifier = Modifier.testTag("parent_device_button")
            )

            if (parentPermissionDenied) {
                Spacer(modifier = Modifier.height(Spacing.space8))
                Text(
                    text = if (parentPermissionPermanentlyDenied) {
                        stringResource(R.string.permission_permanently_denied_parent)
                    } else {
                        stringResource(R.string.permission_rationale_parent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                if (parentPermissionPermanentlyDenied) {
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    OdTextButton(
                        text = stringResource(R.string.open_app_settings),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("parent_open_settings_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.space8))

            Text(
                text = stringResource(R.string.parent_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.space16)
            )

            Spacer(modifier = Modifier.height(Spacing.space32))

            OdTextButton(
                text = stringResource(R.string.settings),
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("settings_button")
            )
        }
    }
}
