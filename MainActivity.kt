@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.daytimer
import android.Manifest
import android.app.*
import android.content.Context
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { Themed { Main(this) } } }
}

val fmt = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)

@Composable fun Main(c: Context) {
    var list by remember { mutableStateOf(Store.load(c)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var add by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    fun upd(l: List<Tm>) { list = l; Store.save(c, l) }
    Scaffold(floatingActionButton = { FloatingActionButton({ add = true }) { Text("+") } }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { t ->
                val diff = (if (t.down) t.at - now else now - t.at).coerceAtLeast(0)
                val over = t.down && (t.done || t.at <= now)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleMedium)
                    if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                    Text(if (over) "انتهى" else "${if (t.down) "باقي" else "مضى"} ${diff / 86400000} يوم و ${diff / 3600000 % 24} ساعة", style = MaterialTheme.typography.headlineSmall)
                    Text(fmt.format(Date(t.at)), style = MaterialTheme.typography.bodySmall)
                    TextButton({ Sched.cancel(c, t); upd(list - t) }) { Text("حذف") } } } } } }
    if (add) AddDlg(c, { add = false }) { upd(list + it); Sched.set(c, it); add = false }
}

@Composable fun AddDlg(c: Context, onX: () -> Unit, onOk: (Tm) -> Unit) {
    var name by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    var down by remember { mutableStateOf(true) }; var at by remember { mutableLongStateOf(System.currentTimeMillis() + 3600000) }
    AlertDialog(onDismissRequest = onX,
        confirmButton = { TextButton({ if (name.isNotBlank()) onOk(Tm(System.currentTimeMillis(), name, note, at, down)) }) { Text("حفظ") } },
        dismissButton = { TextButton(onX) { Text("إلغاء") } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") })
            OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(down, { down = true }, { Text("تنازلي") }); FilterChip(!down, { down = false }, { Text("تصاعدي") }) }
            OutlinedButton({ val k = Calendar.getInstance().apply { timeInMillis = at }
                DatePickerDialog(c, { _, y, m, d -> k.set(y, m, d)
                    TimePickerDialog(c, { _, h, mi -> k.set(Calendar.HOUR_OF_DAY, h); k.set(Calendar.MINUTE, mi); k.set(Calendar.SECOND, 0); at = k.timeInMillis }, k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE), true).show()
                }, k.get(Calendar.YEAR), k.get(Calendar.MONTH), k.get(Calendar.DAY_OF_MONTH)).show() }) { Text(fmt.format(Date(at))) } } })
}
