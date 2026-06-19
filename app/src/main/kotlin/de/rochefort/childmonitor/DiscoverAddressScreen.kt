package de.rochefort.childmonitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverAddressScreen(
    onNavigateBack: () -> Unit,
    onConnect: (String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("10000") }
    var pairingCode by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { AppTopAppBar(stringResource(R.string.enter_address_title), onNavigateBack) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.enterAddressInstructions),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text(stringResource(R.string.ip_address)) },
                placeholder = { Text(stringResource(R.string.exampleAddress)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ip_address_field"),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { char -> char.isDigit() } },
                label = { Text(stringResource(R.string.portTitle)) },
                placeholder = { Text(stringResource(R.string.examplePort)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it },
                label = { Text(stringResource(R.string.pairing_code_optional)) },
                placeholder = { Text(stringResource(R.string.examplePairingCode)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (ipAddress.isNotBlank() && port.isNotBlank()) {
                        onConnect(ipAddress, port.toIntOrNull() ?: 10000, pairingCode)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_button"),
                enabled = ipAddress.isNotBlank() && port.isNotBlank()
            ) {
                Text(stringResource(R.string.connect))
            }
        }
    }
}