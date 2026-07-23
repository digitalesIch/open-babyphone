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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.ListenPrimaryAction
import org.openbabyphone.viewmodel.ListenUiState
import org.openbabyphone.viewmodel.ListenViewModel
import kotlin.math.max
import kotlin.math.min

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

    BackHandler { disconnect() }

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
                resumeOnly
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
        topBar = { AppTopAppBar(childName, disconnect) }
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
                onDisconnect = disconnect,
                modifier = modifier
            )
        }
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
    val activeBars = signalBarCount(signalState, loudness)
    val stateLabel = stringResource(signalState.labelRes)
    val signalContentDescription = stringResource(R.string.audio_signal_content_description, stateLabel)
    val stateColor = when (signalState) {
        AudioSignalState.NoRecentAudio -> MaterialTheme.colorScheme.onSurfaceVariant
        AudioSignalState.Quiet -> MaterialTheme.colorScheme.primary
        AudioSignalState.SoundDetected -> MaterialTheme.colorScheme.primary
        AudioSignalState.LoudSound -> MaterialTheme.colorScheme.secondary
    }
    val backgroundMix = when (signalState) {
        AudioSignalState.NoRecentAudio -> 0.04f
        AudioSignalState.Quiet -> 0.08f
        AudioSignalState.SoundDetected -> 0.14f
        AudioSignalState.LoudSound -> 0.28f
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            verticalAlignment = Alignment.Bottom
        ) {
            repeat(SIGNAL_BAR_COUNT) { index ->
                val filled = index < activeBars
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((18 + index * 9).dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (filled) stateColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        )
                )
            }
        }
        Text(
            text = stringResource(signalState.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

internal fun signalBarCount(signalState: AudioSignalState, loudness: Float): Int = when (signalState) {
    AudioSignalState.NoRecentAudio -> 0
    AudioSignalState.Quiet -> 1
    AudioSignalState.LoudSound -> SIGNAL_BAR_COUNT
    AudioSignalState.SoundDetected -> (loudness * SIGNAL_BAR_COUNT).toInt().coerceIn(2, SIGNAL_BAR_COUNT - 1)
}

private const val AUDIO_SIGNAL_STALE_MS = 2500L
private const val SOUND_SIGNAL_THRESHOLD = 0.06f
private const val LOUD_SIGNAL_THRESHOLD = 0.56f
private const val SIGNAL_BAR_COUNT = 6
