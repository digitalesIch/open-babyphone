package de.rochefort.childmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rochefort.childmonitor.service.ServiceConnectionManager
import de.rochefort.childmonitor.viewmodel.MonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val connection = remember {
        object : android.content.ServiceConnection {
            override fun onServiceConnected(className: android.content.ComponentName?, service: android.os.IBinder?) {}
            override fun onServiceDisconnected(className: android.content.ComponentName?) {}
        }
    }

    DisposableEffect(Unit) {
        ServiceConnectionManager.bindMonitorService(context, lifecycleOwner, viewModel)
        onDispose {
            ServiceConnectionManager.unbindMonitorService(context, connection)
        }
    }

    Scaffold(
        topBar = { AppTopAppBar(stringResource(R.string.childDevice), onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loading_card"),
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_info_card")
                        .semantics { contentDescription = "Service information" },
                    content = {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.serviceTitle), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.serviceName, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.serviceDescription), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_card"),
                    content = {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.pairingCodeTitle), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.pairingCode,
                                onValueChange = { viewModel.updatePairingCode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pairing_code_field"),
                                placeholder = { Text(stringResource(R.string.examplePairingCode)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.pairingCodeDescription), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_info_card"),
                    content = {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.portTitle), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.port.toString(), style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.addressTitle), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.addresses.isEmpty()) {
                                Text(
                                    stringResource(R.string.notConnected),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                uiState.addresses.forEach { address ->
                                    Text(address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                uiState.status,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { contentDescription = "Service status: ${uiState.status}" }
                            )
                        }
                    }
                )
            }
        }
    }
}