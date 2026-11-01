@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.kredi.data.InvoiceItem

@Composable
fun ProductsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val cur by vm.settings.currency.collectAsState()
    val saved by vm.savedProducts.collectAsState()
    val customers by vm.summaries.collectAsState()

    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var showSaved by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InvoiceItem?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val total = vm.invoiceTotal

    fun addToInvoice() {
        val p = parseAmount(price)
        val q = parseAmount(qty)
        if (name.isBlank() || p == null || q == null) {
            ctx.toast("أدخل الاسم والسعر والكمية بشكل صحيح")
            return
        }
        vm.addItem(name.trim(), p, q)
        name = ""
        price = ""
        qty = "1"
    }

    fun saveToList() {
        val p = parseAmount(price)
        if (name.isBlank() || p == null) {
            ctx.toast("أدخل اسم المنتج وسعره لحفظه")
            return
        }
        vm.saveProduct(name.trim(), p)
        ctx.toast("تم حفظ المنتج في القائمة")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("حاسبة الفاتورة") }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("اسم المنتج") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = price, onValueChange = { price = it },
                            label = { Text("السعر") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = qty, onValueChange = { qty = it },
                            label = { Text("الكمية") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { addToInvoice() }, modifier = Modifier.weight(1f)) {
                            Text("إضافة للفاتورة")
                        }
                        OutlinedButton(onClick = { saveToList() }, modifier = Modifier.weight(1f)) {
                            Text("حفظ بالقائمة")
                        }
                    }
                    TextButton(onClick = { showSaved = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("المنتجات المحفوظة (${saved.size})")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.invoice.isEmpty()) {
                    item {
                        Text(
                            "الفاتورة فارغة. أضف منتجات من الأعلى.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(vm.invoice, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${fmt(item.price)} × ${fmt(item.qty)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                money(item.price * item.qty, cur),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { editing = item }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل")
                            }
                            IconButton(onClick = { vm.removeItem(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجمالي (${vm.invoice.size} منتج)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(
                            money(total, cur),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickCustomer = true },
                            enabled = vm.invoice.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("إضافة كدين على زبون") }
                        OutlinedButton(
                            onClick = { if (vm.invoice.isNotEmpty()) confirmClear = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("فاتورة جديدة") }
                    }
                }
            }
        }
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            title = { Text("المنتجات المحفوظة") },
            text = {
                if (saved.isEmpty()) {
                    Text("لا توجد منتجات محفوظة. اكتب اسم المنتج وسعره ثم اضغط «حفظ بالقائمة».")
                } else {
                    Column {
                        Text(
                            "اضغط على المنتج لإضافته للفاتورة مباشرة (الكمية 1)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(saved, key = { it.id }) { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            vm.addItem(p.name, p.price, 1.0)
                                            ctx.toast("أُضيف: ${p.name}")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                            Text(money(p.price, cur), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                    IconButton(onClick = { vm.deleteProduct(p.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text("تم") } }
        )
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { vm.updateItem(it); editing = null }
        )
    }

    if (pickCustomer) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { pickCustomer = false },
            onPick = { id ->
                vm.addInvoiceAsDebt(id)
                pickCustomer = false
                ctx.toast("تمت إضافة الفاتورة كدين")
            }
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "فاتورة جديدة",
            text = "سيتم مسح كل منتجات الفاتورة الحالية. متأكد؟",
            confirmText = "مسح",
            onConfirm = { vm.clearInvoice(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}
