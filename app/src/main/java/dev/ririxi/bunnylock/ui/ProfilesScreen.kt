@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R

@Composable
fun ProfilesScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onExit: () -> Unit,
) {
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader(stringResource(R.string.profiles_title))
            Panel {
                Text(stringResource(R.string.profiles_description), color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { showSaveDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profiles_save_current))
                }
            }
        }
        if (state.profiles.isEmpty()) {
            item {
                Panel {
                    Text(stringResource(R.string.profiles_empty), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                Panel {
                    Text(profile.name, style = MaterialTheme.typography.titleLarge)
                    Text(profileSummary(profile), color = MaterialTheme.colorScheme.onSurface)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.applyProfile(profile) }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.profiles_apply))
                        }
                        OutlinedButton(onClick = { vm.deleteProfile(profile) }) {
                            Text(stringResource(R.string.profiles_delete))
                        }
                    }
                }
                item {
                    Panel {
                        Text(stringResource(R.string.tools_exit_hint), color = MaterialTheme.colorScheme.onSurface)
                        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tools_exit))
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                vm.saveProfile(name)
                showSaveDialog = false
            },
        )
    }
}

@Composable
private fun profileSummary(profile: LockProfile): String {
    val bands =
        profile.bands.entries.joinToString { (group, selected) ->
            "${group.label}: ${selected.sorted().joinToString { group.bandLabel(it) }}"
        }.ifEmpty { stringResource(R.string.profiles_no_bands) }
    val cellDetails =
        profile.cell?.let { form ->
            listOfNotNull(
                form.lteEarfcn.takeIf { it.isNotBlank() }?.let { "LTE EARFCN $it" },
                form.ltePcis.takeIf { it.isNotBlank() }?.let { "LTE PCI $it" },
                form.nrArfcn.takeIf { it.isNotBlank() }?.let { "NR ARFCN $it" },
                form.nrPcis.takeIf { it.isNotBlank() }?.let { "NR PCI $it" },
            ).joinToString(", ")
        }
    val cell = cellDetails?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.profiles_no_cell)
    return stringResource(R.string.profiles_summary, bands, cell)
}

@Composable
private fun SaveProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profiles_save_hint))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profiles_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.profiles_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profiles_cancel)) }
        },
    )
}
