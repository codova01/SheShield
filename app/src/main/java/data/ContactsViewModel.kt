package com.example.sheshield.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ContactStore(app)
    private val _contacts = MutableStateFlow(store.load())
    val contacts: StateFlow<List<TrustedContact>> = _contacts

    companion object { const val MAX = 5 }

    fun canAdd() = _contacts.value.size < MAX

    fun upsert(c: TrustedContact) {
        val list = _contacts.value.toMutableList()
        val i = list.indexOfFirst { it.id == c.id }
        if (i >= 0) list[i] = c else if (canAdd()) list.add(c)
        _contacts.value = list
        store.save(list)
    }

    fun delete(id: String) {
        val list = _contacts.value.filterNot { it.id == id }
        _contacts.value = list
        store.save(list)
    }
}

