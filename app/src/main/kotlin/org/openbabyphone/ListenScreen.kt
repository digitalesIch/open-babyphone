package org.openbabyphone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.openbabyphone.service.ListenSessionState
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.service.isAuthoritativelyActive
import org.openbabyphone.ui.theme.Motion
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.ListenPrimaryAction
import org.openbabyphone.viewmodel.ListenUiState
import org.openbabyphone.viewmodel.ListenViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    requestId: String,
    expectedChildId: String,
    expectedPairingId: String,
    resumeOnly: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPairAgain: () -> Unit = onNavigateBack,
    onConnectionHelp: () -> Unit = onNavigateBack,
    viewModel: ListenViewModel = viewModel(),
    bindListenService: (Context, ListenViewModel, String, String, String, Boolean) ->
        ServiceConnectionManager.ServiceBinding = ServiceConnectionManager::bindListenService,
    disposeServiceBinding: (Context, ServiceConnectionManager.ServiceBinding) -> Unit =
        ServiceConnectionManager::disposeServiceBinding,
    unbindAndStopService: (Context, ServiceConnectionManager.ServiceBinding) -> Unit =
        ServiceConnectionManager::unbindAndStopService,
    stopListenService: (Context) -> Unit = ServiceConnectionManager::stopListenService,
    permissionChecker: (Context, String) -> Boolean = { context, permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
    readinessStatus: (Context) -> ListenReadinessStatus = ListenReadiness::status,
    openNotificationSettings: (Context) -> Unit = { context ->
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
) {
    KeepScreenOn()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val fallbackName = stringResource(R.string.default_child_name)
    var nowMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var serviceStartAllowed by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                permissionChecker(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }
    var currentReadiness by remember { mutableStateOf(readinessStatus(context)) }
    var serviceBinding by remember { mutableStateOf<ServiceConnectionManager.ServiceBinding?>(null) }
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        currentReadiness = readinessStatus(context)
        serviceStartAllowed = true
    }

    val stopAndNavigate: (Boolean, () -> Unit) -> Unit = { removePending, navigate ->
        if (removePending) PendingConnections.store.remove(requestId)
        val binding = serviceBinding
        if (binding != null) {
            unbindAndStopService(context, binding)
            serviceBinding = null
        } else {
            stopListenService(context)
        }
        navigate()
    }
    val disconnect: () -> Unit = { stopAndNavigate(true, onNavigateBack) }
    val requestDisconnect: () -> Unit = {
        if (uiState.sessionState.isAuthoritativelyActive()) {
            showDisconnectDialog = true
        } else {
            disconnect()
        }
    }

    BackHandler { requestDisconnect() }

    DisposableEffect(
        requestId,
        expectedChildId,
        expectedPairingId,
        resumeOnly,
        retryToken,
        serviceStartAllowed
    ) {
        if (!serviceStartAllowed) {
            onDispose { }
        } else {
            val binding = bindListenService(
                context,
                viewModel,
                requestId,
                expectedChildId,
                expectedPairingId,
                resumeOnly && retryToken == 0
            )
            serviceBinding = binding
            onDispose {
                disposeServiceBinding(context, binding)
                if (serviceBinding === binding) serviceBinding = null
            }
        }
    }

    LaunchedEffect(Unit) {
        currentReadiness = readinessStatus(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissionChecker(context, Manifest.permission.POST_NOTIFICATIONS) &&
            !notificationPermissionRequested
        ) {
            notificationPermissionRequested = true
            if (permissionRequester != null) {
                permissionRequester(Manifest.permission.POST_NOTIFICATIONS) {
                    currentReadiness = readinessStatus(context)
                    serviceStartAllowed = true
                }
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            serviceStartAllowed = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentReadiness = readinessStatus(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.sessionState) {
        while (uiState.sessionState is ListenSessionState.Listening) {
            nowMillis = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val childName = uiState.childDeviceName.ifBlank { fallbackName }
    Scaffold(
        topBar = { AppTopAppBar(childName, requestDisconnect) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            ListenContent(
                uiState = uiState,
                childName = childName,
                nowMillis = nowMillis,
                readinessNotice = selectListenReadinessNotice(currentReadiness),
                onPrimaryAction = { action ->
                    when (action) {
                        ListenPrimaryAction.Retry -> retryToken++
                        ListenPrimaryAction.PairAgain -> stopAndNavigate(true, onPairAgain)
                        ListenPrimaryAction.ConnectionHelp -> stopAndNavigate(false, onConnectionHelp)
                    }
                },
                onOpenNotificationSettings = { openNotificationSettings(context) },
                onDisconnect = requestDisconnect,
                modifier = modifier
            )
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.disconnect)) },
            text = { Text(stringResource(R.string.disconnect_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        disconnect()
                    },
                    modifier = Modifier.testTag("confirm_disconnect")
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectDialog = false },
                    modifier = Modifier.testTag("cancel_disconnect")
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun ListenContent(
    uiState: ListenUiState,
    childName: String,
    nowMillis: Long,
    readinessNotice: ListenReadinessNotice?,
    onPrimaryAction: (ListenPrimaryAction) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.space16)
    ) {
        OdOutlinedCard(
            modifier = Modifier
                .testTag("listen_state_hero")
                .semantics {
                    contentDescription = childName
                    stateDescription = uiState.presentation.message
                    traversalIndex = 0f
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.presentation.showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("listen_state_spinner")
                    )
                    Spacer(Modifier.height(Spacing.space16))
                } else if (uiState.sessionState is ListenSessionState.Error ||
                    uiState.sessionState is ListenSessionState.Lost ||
                    uiState.sessionState is ListenSessionState.Disrupted
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(Spacing.space12))
                }

                Text(
                    text = uiState.presentation.message,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W800),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag("listen_primary_message")
                        .semantics {
                            traversalIndex = 0f
                        }
                )
                Spacer(Modifier.height(Spacing.space8))
                Text(
                    text = uiState.presentation.detail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (uiState.sessionState is ListenSessionState.Listening) {
                    Spacer(Modifier.height(Spacing.space24))
                    AudioSignalIndicator(
                        volumeHistory = uiState.volumeHistory,
                        volumeNorm = uiState.volumeNorm,
                        lastAudioUpdateAtMillis = uiState.lastAudioUpdateAtMillis,
                        nowMillis = nowMillis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_signal_indicator")
                    )
                }

                if (readinessNotice != null) {
                    Spacer(Modifier.height(Spacing.space24))
                    ListenReadinessNotice(
                        notice = readinessNotice,
                        onOpenNotificationSettings = onOpenNotificationSettings
                    )
                }

                uiState.presentation.primaryAction?.let { action ->
                    Spacer(Modifier.height(Spacing.space24))
                    OdPrimaryButton(
                        text = stringResource(action.labelRes),
                        onClick = { onPrimaryAction(action) },
                        modifier = Modifier
                            .testTag("listen_primary_action")
                            .semantics { traversalIndex = 2f }
                    )
                }

                Spacer(Modifier.height(Spacing.space16))
                OdOutlinedActionButton(
                    text = stringResource(R.string.disconnect),
                    onClick = onDisconnect,
                    modifier = Modifier
                        .testTag("disconnect_button")
                        .semantics { traversalIndex = 3f }
                )
            }
        }
    }
}

private val ListenPrimaryAction.labelRes: Int
    get() = when (this) {
        ListenPrimaryAction.Retry -> R.string.retry
        ListenPrimaryAction.PairAgain -> R.string.pair_again
        ListenPrimaryAction.ConnectionHelp -> R.string.connection_help
    }

@Composable
private fun ListenReadinessNotice(
    notice: ListenReadinessNotice,
    onOpenNotificationSettings: () -> Unit
) {
    val titleRes: Int
    val detailRes: Int
    when (notice) {
        ListenReadinessNotice.MutedMediaVolume -> {
            titleRes = R.string.listen_readiness_muted_title
            detailRes = R.string.listen_readiness_muted_detail
        }
        ListenReadinessNotice.ConnectionAlertsDisabled -> {
            titleRes = R.string.listen_readiness_notifications_title
            detailRes = R.string.listen_readiness_notifications_detail
        }
        ListenReadinessNotice.ExternalAudioOutput -> {
            titleRes = R.string.listen_readiness_external_output_title
            detailRes = R.string.listen_readiness_external_output_detail
        }
    }
    val title = stringResource(titleRes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .padding(Spacing.space16)
            .testTag("listen_readiness_notice")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = title
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W800)
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = stringResource(detailRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (notice == ListenReadinessNotice.ConnectionAlertsDisabled) {
            Spacer(Modifier.height(Spacing.space8))
            OdTextButton(
                text = stringResource(R.string.open_app_settings),
                onClick = onOpenNotificationSettings,
                modifier = Modifier.testTag("open_notification_settings")
            )
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
    val loudness = if (signalState == AudioSignalState.NoRecentAudio) 0f else {
        rollingLoudness(volumeHistory, volumeNorm)
    }
    val stateLabel = stringResource(signalState.labelRes)
    val signalContentDescription = stringResource(R.string.audio_signal_content_description, stateLabel)
    val configuration = LocalConfiguration.current
    val waveformHeight = (configuration.screenHeightDp * WAVEFORM_SCREEN_HEIGHT_FRACTION)
        .dp
        .coerceIn(WAVEFORM_MIN_HEIGHT, WAVEFORM_MAX_HEIGHT)
    val stateColor = when (signalState) {
        AudioSignalState.NoRecentAudio -> MaterialTheme.colorScheme.onSurfaceVariant
        AudioSignalState.Quiet -> MaterialTheme.colorScheme.primary
        AudioSignalState.SoundDetected -> MaterialTheme.colorScheme.primary
        AudioSignalState.LoudSound -> MaterialTheme.colorScheme.primary
    }
    val backgroundMix by animateFloatAsState(
        targetValue = audioSignalBackgroundMix(signalState, loudness),
        animationSpec = tween(Motion.DurationShort),
        label = "audio signal background"
    )
    val descriptionColor = if (backgroundMix >= AUDIO_SIGNAL_STRONG_CONTENT_MIX) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                lerp(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.primaryContainer,
                    backgroundMix
                )
            )
            .padding(Spacing.space16)
            .semantics {
                contentDescription = signalContentDescription
                stateDescription = stateLabel
                traversalIndex = 1f
            }
            .testTag("audio_freshness"),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.audio_signal_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = stateColor,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(stateColor.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.space12, vertical = Spacing.space4)
            )
        }
        VolumeWaveform(
            volumeHistory = volumeHistory,
            volumeNorm = volumeNorm,
            stale = signalState == AudioSignalState.NoRecentAudio,
            modifier = Modifier
                .fillMaxWidth()
                .height(waveformHeight)
                .testTag("audio_signal_waveform")
        )
        Text(
            text = stringResource(signalState.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = descriptionColor
        )
    }
}

@Composable
private fun VolumeWaveform(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    stale: Boolean,
    modifier: Modifier = Modifier
) {
    val waveformColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        val pointCount = (size.width / WAVEFORM_POINT_SPACING.toPx())
            .toInt()
            .coerceIn(2, WAVEFORM_MAX_POINTS)
        val samples = waveformSamples(volumeHistory, volumeNorm, pointCount)
        val baseline = size.height * WAVEFORM_BASELINE_FRACTION
        val maxHeight = size.height * WAVEFORM_MAX_HEIGHT_FRACTION
        val firstSignal = samples.indexOfFirst { it > WAVEFORM_SILENCE_FLOOR }
        val signalAlpha = if (stale) 0.32f else 1f

        drawLine(
            color = outlineColor.copy(alpha = 0.28f),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx()
        )

        if (firstSignal < 0) return@Canvas

        val waveformPath = Path()
        val fillPath = Path()
        val firstX = waveformX(firstSignal, pointCount, size.width)
        fillPath.moveTo(firstX, baseline)
        samples.forEachIndexed { index, sample ->
            if (index < firstSignal) return@forEachIndexed
            val x = waveformX(index, pointCount, size.width)
            val y = baseline - maxHeight * sample
            if (index == firstSignal) {
                waveformPath.moveTo(x, y)
            } else {
                waveformPath.lineTo(x, y)
            }
            fillPath.lineTo(x, y)
        }
        val lastX = waveformX(samples.lastIndex, pointCount, size.width)
        fillPath.lineTo(lastX, baseline)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.horizontalGradient(
                listOf(
                    waveformColor.copy(alpha = 0.08f * signalAlpha),
                    waveformColor.copy(alpha = 0.24f * signalAlpha),
                    waveformColor.copy(alpha = 0.38f * signalAlpha)
                )
            )
        )
        drawPath(
            path = waveformPath,
            brush = Brush.horizontalGradient(
                listOf(
                    waveformColor.copy(alpha = 0.32f * signalAlpha),
                    waveformColor.copy(alpha = 0.72f * signalAlpha),
                    waveformColor.copy(alpha = signalAlpha)
                )
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private fun waveformX(index: Int, pointCount: Int, width: Float): Float {
    return if (pointCount <= 1) 0f else index.toFloat() / (pointCount - 1) * width
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

internal fun audioSignalBackgroundMix(signalState: AudioSignalState, loudness: Float): Float {
    if (signalState == AudioSignalState.NoRecentAudio) return AUDIO_SIGNAL_STALE_BACKGROUND_MIX
    val loudnessProgress = (loudness / LOUD_SIGNAL_THRESHOLD).coerceIn(0f, 1f)
    return (AUDIO_SIGNAL_BASE_BACKGROUND_MIX +
        loudnessProgress * (AUDIO_SIGNAL_MAX_BACKGROUND_MIX - AUDIO_SIGNAL_BASE_BACKGROUND_MIX))
        .coerceIn(AUDIO_SIGNAL_BASE_BACKGROUND_MIX, AUDIO_SIGNAL_MAX_BACKGROUND_MIX)
}

internal fun waveformSamples(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    pointCount: Int,
    windowSize: Int = WAVEFORM_WINDOW_SAMPLES
): FloatArray {
    if (pointCount <= 0) return FloatArray(0)
    if (volumeHistory.isEmpty() || windowSize <= 0) return FloatArray(pointCount)

    val windowStart = max(0, volumeHistory.size - windowSize)
    val availableSamples = volumeHistory.size - windowStart
    val availablePoints = (pointCount.toLong() * availableSamples / windowSize)
        .toInt()
        .coerceIn(1, pointCount)
    val firstPoint = pointCount - availablePoints
    val result = FloatArray(pointCount)

    for (point in firstPoint until pointCount) {
        val pointIndex = point - firstPoint
        val sampleStartOffset = pointIndex * availableSamples / availablePoints
        val sampleEndOffset = max(
            sampleStartOffset + 1,
            (((pointIndex + 1L) * availableSamples) / availablePoints).toInt()
        ).coerceAtMost(availableSamples)
        val sampleStart = windowStart + sampleStartOffset
        val sampleEnd = windowStart + sampleEndOffset
        var sum = 0f
        var peak = 0f
        var sampleCount = 0
        for (sampleIndex in sampleStart until sampleEnd) {
            val normalized = (volumeHistory[sampleIndex] * volumeNorm).coerceIn(0f, 1f)
            sum += normalized
            peak = max(peak, normalized)
            sampleCount++
        }
        if (sampleCount > 0) {
            val blendedPower = (sum / sampleCount * WAVEFORM_AVERAGE_WEIGHT +
                peak * WAVEFORM_PEAK_WEIGHT).coerceIn(0f, 1f)
            result[point] = sqrt(blendedPower)
        }
    }
    return result
}

internal enum class AudioSignalState(val labelRes: Int, val descriptionRes: Int) {
    NoRecentAudio(R.string.audio_signal_no_audio, R.string.audio_signal_no_audio_description),
    Quiet(R.string.audio_signal_quiet, R.string.audio_signal_quiet_description),
    SoundDetected(R.string.audio_signal_audio_detected, R.string.audio_signal_audio_detected_description),
    LoudSound(R.string.audio_signal_loud_sound, R.string.audio_signal_loud_sound_description)
}

internal fun audioSignalState(
    volumeHistory: FloatArray,
    volumeNorm: Float,
    lastAudioUpdateAtMillis: Long,
    nowMillis: Long
): AudioSignalState {
    if (volumeHistory.isEmpty() || lastAudioUpdateAtMillis <= 0L) return AudioSignalState.NoRecentAudio
    if (nowMillis - lastAudioUpdateAtMillis > AUDIO_SIGNAL_STALE_MS) return AudioSignalState.NoRecentAudio
    val loudness = rollingLoudness(volumeHistory, volumeNorm)
    return when {
        loudness >= LOUD_SIGNAL_THRESHOLD -> AudioSignalState.LoudSound
        loudness >= SOUND_SIGNAL_THRESHOLD -> AudioSignalState.SoundDetected
        else -> AudioSignalState.Quiet
    }
}

private const val AUDIO_SIGNAL_STALE_MS = 2500L
private const val SOUND_SIGNAL_THRESHOLD = 0.06f
private const val LOUD_SIGNAL_THRESHOLD = 0.56f
private const val AUDIO_SIGNAL_BASE_BACKGROUND_MIX = 0.04f
private const val AUDIO_SIGNAL_STALE_BACKGROUND_MIX = 0.02f
private const val AUDIO_SIGNAL_MAX_BACKGROUND_MIX = 0.34f
private const val AUDIO_SIGNAL_STRONG_CONTENT_MIX = 0.25f
private const val WAVEFORM_WINDOW_SAMPLES = 1_500
private const val WAVEFORM_MAX_POINTS = 240
private val WAVEFORM_POINT_SPACING = 2.dp
private const val WAVEFORM_BASELINE_FRACTION = 0.82f
private const val WAVEFORM_MAX_HEIGHT_FRACTION = 0.68f
private const val WAVEFORM_SILENCE_FLOOR = 0.002f
private const val WAVEFORM_AVERAGE_WEIGHT = 0.7f
private const val WAVEFORM_PEAK_WEIGHT = 0.3f
private const val WAVEFORM_SCREEN_HEIGHT_FRACTION = 0.3f
private val WAVEFORM_MIN_HEIGHT = 96.dp
private val WAVEFORM_MAX_HEIGHT = 320.dp
