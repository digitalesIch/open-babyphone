package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
                OdConnectionInfo(
                    label = stringResource(R.string.connected_to),
                    value = {
                        Text(
                            text = uiState.childDeviceName.ifEmpty { name.ifEmpty { unknownLabel } },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.space24))

                OdConnectionInfo(
                    label = stringResource(R.string.status),
                    value = {
                        Row(
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = uiState.status
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OdStatusPill(
                                text = uiState.status,
                                active = uiState.isConnected || uiState.isReconnecting
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
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.space24))

                if (uiState.isError) {
                    OdOutlinedCard(
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
                                OdPrimaryButton(
                                    text = stringResource(R.string.retry),
                                    onClick = onNavigateBack
                                )
                            }
                        }
                    )
                } else if (!uiState.isConnected) {
                    OdOutlinedCard(
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

                    OdOutlinedActionButton(
                        text = stringResource(R.string.disconnect),
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disconnect_button")
                    )
                } else {
                    val waveformShape = MaterialTheme.shapes.large
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(waveformShape)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = waveformShape
                            )
                    ) {
                        VolumeCanvas(
                            volumeHistory = uiState.volumeHistory,
                            volumeNorm = uiState.volumeNorm,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("volume_canvas")
                                .semantics { contentDescription = volumeVisualizationDescription }
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.space16))

                    OdOutlinedActionButton(
                        text = stringResource(R.string.disconnect),
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disconnect_button")
                    )
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
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val height = size.height
        val width = size.width
        val loudness = rollingLoudness(volumeHistory, volumeNorm)
        val loudSignal = ((loudness - 0.34f) / 0.66f).coerceIn(0f, 1f)
        val isDarkTheme = background.luminance() < 0.5f
        val loudBackground = if (isDarkTheme) onSurface else primaryContainer
        val backgroundMix = if (isDarkTheme) {
            0.08f + loudSignal * 0.84f
        } else {
            0.04f + loudSignal * 0.28f
        }
        val backgroundColor = lerp(surfaceVariant, loudBackground, backgroundMix.coerceIn(0f, 0.94f))
        drawRect(backgroundColor)

        val glowAlpha = if (isDarkTheme) 0.10f + loudSignal * 0.18f else 0.07f + loudSignal * 0.10f
        drawCircle(
            color = primary.copy(alpha = glowAlpha),
            radius = max(width, height) * 0.42f,
            center = Offset(width * 0.22f, height * 0.28f)
        )

        val capsuleCount = 7
        val capsuleGap = width * 0.025f
        val capsuleWidth = ((width * 0.55f) - capsuleGap * (capsuleCount - 1)) / capsuleCount
        val capsuleStartX = (width - (capsuleWidth * capsuleCount + capsuleGap * (capsuleCount - 1))) / 2f
        val capsuleBaseY = height * 0.72f
        repeat(capsuleCount) { index ->
            val recent = normalizedRecentSample(volumeHistory, volumeNorm, index, capsuleCount)
            val capsuleHeight = height * (0.10f + recent * 0.22f + loudness * 0.08f)
            val color = if (index % 2 == 0) primary else secondary
            drawRoundRect(
                color = color.copy(alpha = 0.11f + loudSignal * 0.09f),
                topLeft = Offset(capsuleStartX + index * (capsuleWidth + capsuleGap), capsuleBaseY - capsuleHeight),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f)
            )
        }

        if (volumeHistory.isEmpty()) return@Canvas

        val waveInset = width * 0.08f
        val waveWidth = width - waveInset * 2f
        val centerY = height * 0.48f
        val pointCount = min(72, volumeHistory.size).coerceAtLeast(2)
        val sampleStart = (volumeHistory.size - pointCount).coerceAtLeast(0)
        val points = List(pointCount) { index ->
            val sampleIndex = sampleStart + index
            val normalized = (volumeHistory[sampleIndex] * volumeNorm).coerceIn(0f, 1f)
            val phase = sin(index * 0.72f).toFloat()
            val amplitude = height * (0.09f + normalized * 0.22f + loudness * 0.08f)
            Offset(
                x = waveInset + (index.toFloat() / (pointCount - 1)) * waveWidth,
                y = centerY - phase * amplitude
            )
        }
        val primaryPath = smoothPath(points)
        val echoPath = smoothPath(points.map { point ->
            point.copy(y = centerY + (centerY - point.y) * 0.56f)
        })

        val contrastMix = if (isDarkTheme) loudSignal else 0f
        val primaryWave = lerp(primary, onPrimary, contrastMix * 0.82f)
        val secondaryWave = lerp(secondary, onPrimary, contrastMix * 0.68f)
        val lineBrush = Brush.horizontalGradient(
            colors = listOf(primaryWave, secondaryWave),
            startX = waveInset,
            endX = width - waveInset
        )

        drawPath(
            path = echoPath,
            brush = lineBrush,
            alpha = 0.28f + loudSignal * 0.12f,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(
            path = primaryPath,
            brush = lineBrush,
            style = Stroke(
                width = 4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun rollingLoudness(volumeHistory: FloatArray, volumeNorm: Float): Float {
    if (volumeHistory.isEmpty()) return 0f
    val sampleCount = min(volumeHistory.size, 48)
    var peak = 0f
    var sum = 0f
    for (index in volumeHistory.size - sampleCount until volumeHistory.size) {
        val normalized = (volumeHistory[index] * volumeNorm).coerceIn(0f, 1f)
        peak = max(peak, normalized)
        sum += normalized
    }
    val average = sum / sampleCount
    return (average * 0.65f + peak * 0.35f).coerceIn(0f, 1f)
}

private fun normalizedRecentSample(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    index: Int,
    count: Int
): Float {
    if (volumeHistory.isEmpty()) return 0f
    val sampleIndex = ((index + 1f) / count * volumeHistory.size).toInt()
        .coerceIn(0, volumeHistory.lastIndex)
    return (volumeHistory[sampleIndex] * volumeNorm).coerceIn(0f, 1f)
}

private fun smoothPath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            quadraticTo(
                previous.x,
                previous.y,
                (previous.x + current.x) / 2f,
                (previous.y + current.y) / 2f
            )
        }
        lineTo(points.last().x, points.last().y)
    }
}
