package com.stoco.qrscanner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoco.qrscanner.data.CsvExporter
import com.stoco.qrscanner.data.Sensor
import com.stoco.qrscanner.data.SensorDao
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(dao: SensorDao, sensors: List<Sensor>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    val filtered = remember(sensors, query) {
        val q = query.trim()
        if (q.isEmpty()) sensors
        else sensors.filter { it.pn.contains(q, true) || it.devEui.contains(q, true) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("${sensors.size} sensors") },
                actions = {
                    IconButton(enabled = sensors.isNotEmpty(), onClick = {
                        scope.launch { context.startActivity(CsvExporter.shareIntent(context, dao.getAll())) }
                    }) { Icon(Icons.Default.Share, "Share CSV") }
                    IconButton(enabled = sensors.isNotEmpty(), onClick = {
                        scope.launch {
                            val where = CsvExporter.saveToDownloads(context, dao.getAll())
                            Toast.makeText(context, where?.let { "Saved to $it" } ?: "Save failed", Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Default.Download, "Save CSV") }
                    IconButton(enabled = sensors.isNotEmpty(), onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search PN or DEVEUI") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (sensors.isEmpty()) "No sensors yet. Go scan some!" else "No matches")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { s ->
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it != SwipeToDismissBoxValue.Settled) {
                            scope.launch {
                                dao.delete(s)
                                val res = snackbar.showSnackbar("Deleted ${s.pn}", "Undo", duration = SnackbarDuration.Short)
                                if (res == SnackbarResult.ActionPerformed) dao.insert(s)
                            }
                            true
                        } else false
                    })
                    SwipeToDismissBox(state, backgroundContent = {
                        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterEnd) { Text("Delete", color = Color(0xFFC62828)) }
                    }) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.pn, fontWeight = FontWeight.Bold)
                                    Text(fmt.format(Date(s.scannedAt)), fontSize = 12.sp)
                                }
                                Mono("DEVEUI", s.devEui); Mono("APPEUI", s.appEui); Mono("APPKEY", s.appKey)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all ${sensors.size} sensors?") },
            text = { Text("This cannot be undone. Export first if you need the data.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { dao.clear() } }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Mono(label: String, value: String) {
    Text("$label  $value", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}
