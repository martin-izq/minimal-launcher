package com.martin.minimallauncher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.martin.minimallauncher.R
import com.martin.minimallauncher.data.AppInfo

/** Dialog to give an app a custom visible name. */
@Composable
fun RenameDialog(
    app: AppInfo,
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(app.packageName) { mutableStateOf(currentName) }
    MinimalDialog(onDismiss) {
        DialogTitle(stringResource(R.string.rename_title))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text(stringResource(R.string.rename_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DialogActions {
            DialogButton(stringResource(R.string.common_cancel), emphasized = false, onClick = onDismiss)
            DialogButton(stringResource(R.string.common_save), onClick = { onConfirm(text); onDismiss() })
        }
    }
}
