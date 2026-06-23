package org.openbabyphone

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.viewmodel.ListenViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    address: String,
    port: Int,
    name: String,
    pairingCode: String,
    resumeOnly: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListenViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val unknownLabel = stringResource(R.string.unknown_device)
    val statusColor by animateColorAsState(
        targetValue = when {
            uiState.isError -> MaterialTheme.colorScheme.errorContainer
            uiState.isConnected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "statusColor"
    )

    DisposableEffect(address, port, name, pairingCode, resumeOnly) {
        val binding = ServiceConnectionManager.bindListenService(
            context,
            viewModel,
            address,
            port,
            name,
            pairingCode,
            resumeOnly
        )
        onDispose {
            ServiceConnectionManager.unbindAndStopService(context, binding)
        }
    }

    Scaffold(
        topBar = { AppTopAppBar(stringResource(R.string.parentDevice), onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.connectedTo),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.childDeviceName.ifEmpty { name.ifEmpty { unknownLabel } },
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.status),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                uiState.status,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .background(statusColor, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = uiState.status
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isError) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("error_card"),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.connection_lost),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onNavigateBack) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                )
            } else {
                VolumeCanvas(
                    volumeHistory = uiState.volumeHistory,
                    volumeNorm = uiState.volumeNorm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("volume_canvas")
                        .semantics { contentDescription = "Volume visualization" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disconnect_button")
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }
        }
    }
}

@Composable
private fun VolumeCanvas(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (volumeHistory.isEmpty()) return@Canvas

        val height = size.height
        val width = size.width
        val size = volumeHistory.size
        val minBrightness = 0.3f
        val relativeBrightness = if (size > 0) {
            (volumeNorm * volumeHistory[size - 1]).coerceAtLeast(minBrightness)
        } else {
            minBrightness
        }

        val blue: Float
        val rest: Float
        if (relativeBrightness > 0.5f) {
            blue = 1.0f
            rest = (2 * (relativeBrightness - 0.5f))
        } else {
            blue = ((relativeBrightness - 0.2f) / 0.3f).coerceIn(0f, 1f)
            rest = 0f
        }

        val backgroundColor = Color(rest, rest, blue)
        drawRect(backgroundColor)

        if (size == 0) return@Canvas

        val margins = height * 0.1f
        val graphHeight = height - 2.0f * margins
        val leftMost = (volumeHistory.size - width.toInt()).coerceAtLeast(0)
        val graphScale = graphHeight * volumeNorm

        val paintColor = Color(255, 127, 0)

        var xPrev = 0f
        var yPrev = margins + graphHeight - volumeHistory[leftMost] * graphScale
        val length = min(size, width.toInt())

        for (xNext in 1 until length - 1) {
            val yNext = margins + graphHeight - volumeHistory[leftMost + xNext] * graphScale
            drawLine(
                color = paintColor,
                start = Offset(xPrev, yPrev),
                end = Offset(xNext.toFloat(), yNext),
                strokeWidth = 2f
            )
            xPrev = xNext.toFloat()
            yPrev = yNext
        }
    }
}
