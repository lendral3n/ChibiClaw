package com.chibiclaw.executor.tier2

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.RawContacts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 11 — Contact CRUD: add, edit, delete contacts.
 */
@Singleton
class ContactWriteExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun perform(operation: String, name: String, phone: String, email: String, contactId: String): String {
        val op = operation.trim().lowercase()
        return try {
            when (op) {
                "add", "create", "tambah", "buat" -> addContact(name, phone, email)
                "edit", "update", "ubah" -> editContact(contactId, name, phone, email)
                "delete", "remove", "hapus" -> deleteContact(contactId, name)
                "search", "cari", "find" -> searchContact(name)
                else -> "contact_error: unknown operation '$op'"
            }
        } catch (e: SecurityException) {
            "contact_error: WRITE_CONTACTS permission not granted"
        } catch (e: Exception) {
            "contact_error: ${e.message}"
        }
    }

    private fun addContact(name: String, phone: String, email: String): String {
        if (name.isBlank()) return "contact_error: name is required"
        val ops = ArrayList<ContentProviderOperation>()

        // Raw contact
        ops.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, null)
                .withValue(RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // Name
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        // Phone
        if (phone.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
        }

        // Email
        if (email.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.DATA, email)
                    .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME)
                    .build()
            )
        }

        val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawContactUri = results[0].uri
        val rawContactId = rawContactUri?.let { ContentUris.parseId(it) } ?: -1
        return "contact_added: $name (id=$rawContactId) phone=$phone email=$email"
    }

    private fun editContact(contactId: String, name: String, phone: String, email: String): String {
        if (contactId.isBlank()) return "contact_error: contactId required for edit. Use search first."
        val id = contactId.toLongOrNull() ?: return "contact_error: invalid contactId"

        val ops = ArrayList<ContentProviderOperation>()

        if (name.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(id.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
        }

        if (phone.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(id.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    )
                    .withValue(CommonDataKinds.Phone.NUMBER, phone)
                    .build()
            )
        }

        if (ops.isEmpty()) return "contact_noop: nothing to update"
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return "contact_updated: id=$id"
    }

    private fun deleteContact(contactId: String, name: String): String {
        // Try by ID first, then by name
        val uri: Uri = if (contactId.isNotBlank()) {
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
        } else if (name.isNotBlank()) {
            // Find the contact ID by name first
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
                } else {
                    return "contact_error: not found '$name'"
                }
            } ?: return "contact_error: query failed"
        } else {
            return "contact_error: contactId or name required"
        }

        val deleted = context.contentResolver.delete(uri, null, null)
        return if (deleted > 0) "contact_deleted: $name" else "contact_error: delete failed"
    }

    private fun searchContact(query: String): String {
        if (query.isBlank()) return "contact_error: search query required"
        val cursor = context.contentResolver.query(
            CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                CommonDataKinds.Phone.DISPLAY_NAME,
                CommonDataKinds.Phone.NUMBER
            ),
            "${CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null
        )
        cursor ?: return "contact_search: no results"
        val sb = StringBuilder("[contacts] search: $query\n")
        var count = 0
        cursor.use {
            while (it.moveToNext() && count < 10) {
                val id = it.getLong(0)
                val name = it.getString(1) ?: ""
                val phone = it.getString(2) ?: ""
                sb.append("• id=$id | $name | $phone\n")
                count++
            }
        }
        if (count == 0) sb.append("(no results)")
        return sb.toString()
    }
}
