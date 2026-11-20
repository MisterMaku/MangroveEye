package com.example.mangrove.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mangrove.data.MapInfo

/**
 * Dialog form for entering map information before adding a new map.
 */
@Composable
fun MapInfoForm(
    initialInfo: MapInfo = MapInfo(),
    onConfirm: (MapInfo) -> Unit,
    onCancel: () -> Unit
) {
    // 1. Capture context here so it can be used inside onClick below
    val context = LocalContext.current

    var name by remember { mutableStateOf(initialInfo.name) }
    var location by remember { mutableStateOf(initialInfo.location) }
    var description by remember { mutableStateOf(initialInfo.description) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Map Information", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Map Name / Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location / Study Area") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Map name is required", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(MapInfo(
                            name = name,
                            location = location,
                            description = description
                        ))
                    }
                }
            ) {
                Text("Save and Finish")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
