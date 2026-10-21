package app.bitcall.data

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<String>,
    val starred: Boolean,
    val photoUri: String?,
) {
    val primary: String get() = numbers.firstOrNull().orEmpty()
}

/** Everything shown on the contact details page. */
data class ContactInfo(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val lookupUri: Uri,
    val phones: List<Pair<String, String>>, // label to number
    val emails: List<String>,
    val addresses: List<String>,
    val company: String?,
    val birthday: String?,
    val notes: String?,
)

object ContactsRepo {
    const val GOOGLE = "com.google"

    suspend fun all(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        val byId = LinkedHashMap<Long, Contact>()
        runCatching {
            context.contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Phone.CONTACT_ID,
                    CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    CommonDataKinds.Phone.NUMBER,
                    CommonDataKinds.Phone.STARRED,
                    CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null,
                "${CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(2) ?: continue
                    val existing = byId[id]
                    if (existing != null) {
                        if (existing.numbers.none { Phone.key(it) == Phone.key(number) })
                            byId[id] = existing.copy(numbers = existing.numbers + number)
                    } else {
                        byId[id] = Contact(id, c.getString(1) ?: number, listOf(number), c.getInt(3) == 1, c.getString(4))
                    }
                }
            }
        }
        byId.values.toList()
    }

    /** Name for a number, via the PhoneLookup index. */
    fun lookup(context: Context, number: String?): Contact? {
        if (number.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.STARRED,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) Contact(c.getLong(0), c.getString(1), listOf(number), c.getInt(2) == 1, c.getString(3)) else null
            }
        }.getOrNull()
    }

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI, values,
            "${ContactsContract.Contacts._ID}=?", arrayOf(contactId.toString()),
        )
    }

    /**
     * Google accounts that already sync contacts on this phone. Reading RawContacts only needs
     * READ_CONTACTS, unlike AccountManager which hides accounts from third-party apps.
     */
    fun googleAccounts(context: Context): List<String> = runCatching {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=?", arrayOf(GOOGLE), null,
        )?.use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let(::add) }.toList() }
    }.getOrNull().orEmpty()

    /** Saves a contact into the chosen Google account so it syncs to contacts.google.com. */
    fun add(context: Context, account: String?, name: String, number: String, email: String?, favorite: Boolean) {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, if (account != null) GOOGLE else null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account)
                .withValue(ContactsContract.RawContacts.STARRED, if (favorite) 1 else 0)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Phone.NUMBER, number)
                .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                .build(),
        )
        if (!email.isNullOrBlank()) ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.Email.ADDRESS, email)
            .build()
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    /** Extra details for Magic Cue: emails, company, notes. */
    fun details(context: Context, contactId: Long): List<String> = runCatching {
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.CONTACT_ID}=?", arrayOf(contactId.toString()), null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val v = c.getString(1) ?: continue
                    when (c.getString(0)) {
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> add("✉ $v")
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> add("🏢 $v")
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> add("📍 $v")
                        CommonDataKinds.Note.CONTENT_ITEM_TYPE -> add("📝 $v")
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> add("🎂 $v")
                    }
                }
            }
        }
    }.getOrNull().orEmpty()

    fun info(context: Context, contactId: Long): ContactInfo? = runCatching {
        val cr = context.contentResolver
        val head = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.STARRED, ContactsContract.Contacts.LOOKUP_KEY,
            ),
            "${ContactsContract.Contacts._ID}=?", arrayOf(contactId.toString()), null,
        )?.use { c -> if (c.moveToFirst()) listOf(c.getString(0), c.getString(1), c.getInt(2).toString(), c.getString(3)) else null }
            ?: return@runCatching null
        val phones = mutableListOf<Pair<String, String>>()
        val emails = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        var company: String? = null
        var birthday: String? = null
        var notes: String? = null
        cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.CONTACT_ID}=?", arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                when (c.getString(0)) {
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        if (phones.none { Phone.key(it.second) == Phone.key(v) }) phones += CommonDataKinds.Phone.getTypeLabel(
                            context.resources, c.getInt(2), c.getString(3),
                        ).toString() to v
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails += v
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> addresses += v
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> company = v
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (c.getInt(2) == CommonDataKinds.Event.TYPE_BIRTHDAY) birthday = v
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> notes = v
                }
            }
        }
        ContactInfo(
            contactId, head[0] ?: "", head[1], head[2] == "1",
            ContactsContract.Contacts.getLookupUri(contactId, head[3]),
            phones, emails.distinct(), addresses.distinct(), company, birthday, notes,
        )
    }.getOrNull()
}
