package com.daftar.kredi.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.daftar.kredi.data.AppDatabase
import com.daftar.kredi.data.Customer
import com.daftar.kredi.data.CustomerSummary
import com.daftar.kredi.data.InvoiceItem
import com.daftar.kredi.data.SavedProduct
import com.daftar.kredi.data.SettingsStore
import com.daftar.kredi.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    val settings = SettingsStore(app)

    val summaries: StateFlow<List<CustomerSummary>> =
        combine(dao.customers(), dao.allTxns()) { customers, txns ->
            val byCustomer = txns.groupBy { it.customerId }
            customers.map { c ->
                val t = byCustomer[c.id].orEmpty()
                CustomerSummary(
                    customer = c,
                    balance = t.sumOf { if (it.isDebt) it.amount else -it.amount },
                    lastActivity = t.maxOfOrNull { it.timestamp } ?: c.createdAt
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTxns: StateFlow<List<Txn>> =
        dao.allTxns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedProducts: StateFlow<List<SavedProduct>> =
        dao.products().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun customerFlow(id: Long) = dao.customer(id)
    fun txnsFlow(id: Long) = dao.txnsOf(id)

    // ---------- الزبائن ----------
    fun addCustomer(name: String, phone: String, note: String) {
        viewModelScope.launch { dao.insertCustomer(Customer(name = name, phone = phone, note = note)) }
    }

    fun updateCustomer(c: Customer) {
        viewModelScope.launch { dao.updateCustomer(c) }
    }

    fun deleteCustomer(id: Long) {
        viewModelScope.launch { dao.deleteCustomer(id) }
    }

    // ---------- العمليات ----------
    fun addTxn(customerId: Long, isDebt: Boolean, amount: Double, note: String) {
        viewModelScope.launch {
            dao.insertTxn(Txn(customerId = customerId, isDebt = isDebt, amount = amount, note = note))
        }
    }

    fun updateTxn(t: Txn) {
        viewModelScope.launch { dao.updateTxn(t) }
    }

    fun deleteTxn(id: Long) {
        viewModelScope.launch { dao.deleteTxn(id) }
    }

    // ---------- المنتجات المحفوظة ----------
    fun saveProduct(name: String, price: Double) {
        viewModelScope.launch { dao.insertProduct(SavedProduct(name = name, price = price)) }
    }

    fun deleteProduct(id: Long) {
        viewModelScope.launch { dao.deleteProduct(id) }
    }

    // ---------- الفاتورة الحالية (في الذاكرة) ----------
    val invoice = mutableStateListOf<InvoiceItem>()
    private var nextItemId = 1L

    val invoiceTotal: Double
        get() = invoice.sumOf { it.price * it.qty }

    fun addItem(name: String, price: Double, qty: Double) {
        invoice.add(InvoiceItem(nextItemId++, name, price, qty))
    }

    fun updateItem(item: InvoiceItem) {
        val i = invoice.indexOfFirst { it.id == item.id }
        if (i >= 0) invoice[i] = item
    }

    fun removeItem(id: Long) {
        invoice.removeAll { it.id == id }
    }

    fun clearInvoice() {
        invoice.clear()
    }

    fun addInvoiceAsDebt(customerId: Long) {
        val total = invoiceTotal
        if (total <= 0.0) return
        val note = "فاتورة: " + invoice.joinToString("، ") { "${it.name}×${fmt(it.qty)}" }
        addTxn(customerId, true, total, note)
        invoice.clear()
    }

    // ---------- النسخ الاحتياطي ----------
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val customers = dao.customersOnce()
        val txns = dao.txnsOnce()
        val products = dao.productsOnce()
        val root = JSONObject()
        root.put("app", "daftar-kredi")
        root.put("version", 1)
        val cArr = JSONArray()
        customers.forEach {
            cArr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)
                    .put("note", it.note).put("createdAt", it.createdAt)
            )
        }
        val tArr = JSONArray()
        txns.forEach {
            tArr.put(
                JSONObject().put("id", it.id).put("customerId", it.customerId).put("isDebt", it.isDebt)
                    .put("amount", it.amount).put("note", it.note).put("timestamp", it.timestamp)
            )
        }
        val pArr = JSONArray()
        products.forEach {
            pArr.put(JSONObject().put("id", it.id).put("name", it.name).put("price", it.price))
        }
        root.put("customers", cArr)
        root.put("txns", tArr)
        root.put("products", pArr)
        root.toString(2)
    }

    suspend fun importJson(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(text)
            val cArr = root.getJSONArray("customers")
            val tArr = root.getJSONArray("txns")
            val pArr = root.optJSONArray("products") ?: JSONArray()
            val customers = (0 until cArr.length()).map {
                val o = cArr.getJSONObject(it)
                Customer(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    note = o.optString("note", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
            val txns = (0 until tArr.length()).map {
                val o = tArr.getJSONObject(it)
                Txn(
                    id = o.getLong("id"),
                    customerId = o.getLong("customerId"),
                    isDebt = o.getBoolean("isDebt"),
                    amount = o.getDouble("amount"),
                    note = o.optString("note", ""),
                    timestamp = o.getLong("timestamp")
                )
            }
            val products = (0 until pArr.length()).map {
                val o = pArr.getJSONObject(it)
                SavedProduct(id = o.getLong("id"), name = o.getString("name"), price = o.getDouble("price"))
            }
            db.withTransaction {
                dao.clearTxns()
                dao.clearCustomers()
                dao.clearProducts()
                dao.insertCustomers(customers)
                dao.insertTxns(txns)
                dao.insertProducts(products)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
