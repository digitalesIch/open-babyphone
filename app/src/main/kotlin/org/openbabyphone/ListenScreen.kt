package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.viewmodel.ListenViewModel
import kotlin.math.max
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
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    var serviceBinding by remember { mutableStateOf<ServiceConnectionManager.ServiceBinding?>(null) }
    val disconnect: () -> Unit = {
        val binding = serviceBinding
        if (binding != null && !binding.stopOnDispose) {
            ServiceConnectionManager.unbindAndStopService(context, binding)
            serviceBinding = null
        }
        onNavigateBack()
    }
    DisposableEffect(address, port, name, pairingCode, resumeOnly, retryToken) {
        val binding = ServiceConnectionManager.bindListenService(
            context,
            viewModel,
            address,
            port,
            name,
            pairingCode,
            resumeOnly
        )
        serviceBinding = binding
        onDispose {
            ServiceConnectionManager.disposeServiceBinding(context, binding)
            if (serviceBinding === binding) {
                serviceBinding = null
            }
        }
    }

    LaunchedEffect(uiState.isConnected) {
        while (uiState.isConnected) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
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
                                    onClick = { retryToken++ },
                                    modifier = Modifier.testTag("retry_button")
                                )
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                OdOutlinedActionButton(
                                    text = stringResource(R.string.disconnect),
                                    onClick = disconnect,
                                    modifier = Modifier.testTag("back_to_discovery_button")
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
                        onClick = disconnect,
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
                        AudioSignalIndicator(
                            volumeHistory = uiState.volumeHistory,
                            volumeNorm = uiState.volumeNorm,
                            lastAudioUpdateAtMillis = uiState.lastAudioUpdateAtMillis,
                            nowMillis = nowMillis,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("audio_signal_indicator")
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.space16))

                    OdOutlinedActionButton(
                        text = stringResource(R.string.disconnect),
                        onClick = disconnect,
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
private fun AudioSignalIndicator(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    lastAudioUpdateAtMillis: Long,
    nowMillis: Long,
    modifier: Modifier = Modifier
) {
    val signalState = audioSignalState(volumeHistory, volumeNorm, lastAudioUpdateAtMillis, nowMillis)
    val loudness = if (signalState == AudioSignalState.NoAudio) 0f else rollingLoudness(volumeHistory, volumeNorm)
    val levelPercent = (loudness * 100).toInt().coerceIn(0, 100)
    val activeBars = signalBarCount(signalState, loudness)
    val stateLabel = stringResource(signalState.labelRes)
    val stateColor = when (signalState) {
        AudioSignalState.NoAudio -> MaterialTheme.colorScheme.onSurfaceVariant
        AudioSignalState.AudioDetected -> MaterialTheme.colorScheme.primary
        AudioSignalState.LoudSound -> MaterialTheme.colorScheme.secondary
    }
    val backgroundMix = when (signalState) {
        AudioSignalState.NoAudio -> 0.04f
        AudioSignalState.AudioDetected -> 0.12f
        AudioSignalState.LoudSound -> 0.28f
    }
    val contentDescription = stringResource(R.string.audio_signal_content_description, stateLabel, levelPercent)

    Column(
        modifier = modifier
            .background(
                lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primaryContainer,
                    backgroundMix
                )
            )
            .padding(Spacing.space24)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.audio_signal_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = stateColor,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(stateColor.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space4)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(SIGNAL_BAR_COUNT) { index ->
                val filled = index < activeBars
                val color = if (filled) {
                    if (signalState == AudioSignalState.LoudSound && index >= 3) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((24 + index * 12).dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(color)
                )
            }
        }

        Column {
            Text(
                text = stringResource(R.string.audio_signal_level_percent, levelPercent),
                style = MaterialTheme.typography.displaySmall,
                color = stateColor
            )
            Spacer(modifier = Modifier.height(Spacing.space8))
            Text(
                text = stringResource(signalState.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun rollingLoudness(volumeHistory: FloatArray, volumeNorm: Float): Float {
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

internal fun normalizedRecentSample(
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

internal enum class AudioSignalState(
    val labelRes: Int,
    val descriptionRes: Int
) {
    NoAudio(R.string.audio_signal_no_audio, R.string.audio_signal_no_audio_description),
    AudioDetected(R.string.audio_signal_audio_detected, R.string.audio_signal_audio_detected_description),
    LoudSound(R.string.audio_signal_loud_sound, R.string.audio_signal_loud_sound_description)
}

internal fun audioSignalState(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    lastAudioUpdateAtMillis: Long,
    nowMillis: Long
): AudioSignalState {
    if (volumeHistory.isEmpty() || lastAudioUpdateAtMillis <= 0L) return AudioSignalState.NoAudio
    if (nowMillis - lastAudioUpdateAtMillis > AUDIO_SIGNAL_STALE_MS) return AudioSignalState.NoAudio
    return if (rollingLoudness(volumeHistory, volumeNorm) >= LOUD_SIGNAL_THRESHOLD) {
        AudioSignalState.LoudSound
    } else {
        AudioSignalState.AudioDetected
    }
}

internal fun signalBarCount(signalState: AudioSignalState, loudness: Float): Int {
    return when (signalState) {
        AudioSignalState.NoAudio -> 0
        AudioSignalState.LoudSound -> SIGNAL_BAR_COUNT
        AudioSignalState.AudioDetected -> (loudness * SIGNAL_BAR_COUNT).toInt().coerceIn(1, SIGNAL_BAR_COUNT - 1)
    }
}

private const val AUDIO_SIGNAL_STALE_MS = 2500L
private const val LOUD_SIGNAL_THRESHOLD = 0.56f
private const val SIGNAL_BAR_COUNT = 6
