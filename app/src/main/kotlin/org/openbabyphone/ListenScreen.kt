package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.lerp
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
    val volumeVisualizationDescription = stringResource(R.string.volume_visualization_content_description)
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
        topBar = { AppTopAppBar(stringResource(R.string.parent_device), onNavigateBack) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(Spacing.space16)
            ) {
                Text(
                    stringResource(R.string.connected_to),
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(Spacing.space8))

                Text(
                    text = uiState.childDeviceName.ifEmpty { name.ifEmpty { unknownLabel } },
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(Spacing.space24))

                Text(
                    stringResource(R.string.status),
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(Spacing.space8))

                Row(
                    modifier = Modifier
                        .background(statusColor, MaterialTheme.shapes.small)
                        .padding(horizontal = Spacing.space12, vertical = Spacing.space8)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = uiState.status
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIndicator(
                        color = when {
                            uiState.isError -> MaterialTheme.colorScheme.error
                            uiState.isConnected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(Spacing.space8))
                    Text(
                        uiState.status,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (uiState.isReconnecting) {
                        Spacer(modifier = Modifier.width(Spacing.space8))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(Spacing.space16)
                                .testTag("reconnecting_spinner"),
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.space24))

                if (uiState.isError) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_card"),
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.space24),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(
                                    stringResource(R.string.connection_lost),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(Spacing.space16))
                                Button(onClick = onNavigateBack) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    )
                } else if (!uiState.isConnected) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waiting_card"),
                        content = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.space24),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.testTag("connection_spinner")
                                )
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(
                                    stringResource(R.string.waiting_for_connection),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Spacing.space16))

                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disconnect_button")
                    ) {
                        Text(stringResource(R.string.disconnect))
                    }
                } else {
                    VolumeCanvas(
                        volumeHistory = uiState.volumeHistory,
                        volumeNorm = uiState.volumeNorm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("volume_canvas")
                            .semantics { contentDescription = volumeVisualizationDescription }
                    )

                    Spacer(modifier = Modifier.height(Spacing.space16))

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
}

@Composable
private fun StatusIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(Spacing.space8)) {
        drawCircle(color = color)
    }
}

@Composable
private fun VolumeCanvas(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val waveformColor = MaterialTheme.colorScheme.secondary

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

        val backgroundColor = lerp(surfaceVariant, primaryContainer, relativeBrightness)
        drawRect(backgroundColor)

        if (size == 0) return@Canvas

        val margins = height * 0.1f
        val graphHeight = height - 2.0f * margins
        val leftMost = (volumeHistory.size - width.toInt()).coerceAtLeast(0)
        val graphScale = graphHeight * volumeNorm

        var xPrev = 0f
        var yPrev = margins + graphHeight - volumeHistory[leftMost] * graphScale
        val length = min(size, width.toInt())

        for (xNext in 1 until length - 1) {
            val yNext = margins + graphHeight - volumeHistory[leftMost + xNext] * graphScale
            drawLine(
                color = waveformColor,
                start = Offset(xPrev, yPrev),
                end = Offset(xNext.toFloat(), yNext),
                strokeWidth = 2f
            )
            xPrev = xNext.toFloat()
            yPrev = yNext
        }
    }
}
