package com.ratig.app.feature.workers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Single selectable option for [DropdownField]. */
data class DropdownOption(
    val value: String?,
    val label: String,
)

/**
 * Read-only exposed dropdown used by the worker edit form and the start-test
 * dialog. [enabled] = false shows the field grayed out; an empty [options]
 * list shows [emptyHint] instead of selectable entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<DropdownOption>,
    selectedValue: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    emptyHint: String = "Tidak ada pilihan",
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.value == selectedValue }?.label.orEmpty()

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { expanded = it && enabled },
        ) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(label) },
                placeholder = { if (options.isEmpty()) Text(emptyHint) },
                supportingText = supportingText?.let { hint -> { Text(hint) } },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(emptyHint) },
                        onClick = { expanded = false },
                        enabled = false,
                    )
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Small pill for the worker active/inactive status ("Aktif" / "Nonaktif"). */
@Composable
fun WorkerStatusBadge(active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Text(
            text = if (active) "Aktif" else "Nonaktif",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
