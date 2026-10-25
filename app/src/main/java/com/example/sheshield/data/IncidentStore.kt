package com.example.sheshield.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LocationPoint(val time: Long, val lat: Double?, val lng: Double?)

/** An encrypted file attached to an incident (for now: audio clips). */
data class Attachment(
    val fileName: String,
    val label: String,
    val sha256: String,
    val savedAt: Long,
    val kind: String
)

data class Incident(
    val id: String,
    val type: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val points: List<LocationPoint> = emptyList(),
    val alerted: List<String> = emptyList(),
    val notes: String = "",
    val attachments: List<Attachment> = emptyList()
)

data class StoredIncident(
    val incident: Incident,
    val sha256: String,
    val savedAt: Long,
    val verified: Boolean
)

/**
 * Every incident is one encrypted file (AES-GCM, key in the Android Keystore).
 * Inside is the incident JSON plus its SHA-256 fingerprint and save timestamp.
 * Audio clips are separate encrypted files, listed inside the incident with their own fingerprint.
 */
object IncidentStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "vault").apply { mkdirs() }

    private fun fileFor(context: Context, id: String) = File(dir(context), "incident_$id.enc")

    @Synchronized
    fun create(context: Context, type: String): String {
        val incident = Incident(
            id = UUID.randomUUID().toString(),
            type = type,
            startedAt = System.currentTimeMillis()
        )
        save(context, incident)
        return incident.id
    }

    @Synchronized
    fun addPoint(context: Context, id: String, lat: Double?, lng: Double?) =
        update(context, id) {
            it.copy(points = it.points + LocationPoint(System.currentTimeMillis(), lat, lng))
        }

    @Synchronized
    fun addAlerted(context: Context, id: String, names: List<String>) =
        update(context, id) { it.copy(alerted = (it.alerted + names).distinct()) }

    @Synchronized
    fun finish(context: Context, id: String) =
        update(context, id) { it.copy(endedAt = System.currentTimeMillis()) }

    @Synchronized
    fun setNotes(context: Context, id: String, notes: String) =
        update(context, id) { it.copy(notes = notes) }

    /** Encrypts a plain audio file, attaches it to the incident, then deletes the plain file. */
    @Synchronized
    fun addAudio(context: Context, id: String, plainFile: File, label: String) {
        try {
            val bytes = plainFile.readBytes()
            val hash = Crypto.sha256Hex(bytes)
            val name = "aud_${UUID.randomUUID()}.enc"
            File(dir(context), name).writeBytes(Crypto.encrypt(bytes))
            val attachment = Attachment(name, label, hash, System.currentTimeMillis(), "audio")
            update(context, id) { it.copy(attachments = it.attachments + attachment) }
        } catch (e: Exception) {
            Log.e("SheShield", "Could not save audio clip", e)
        } finally {
            plainFile.delete()
        }
    }

    /** Decrypts an attachment into the cache folder for playback. The caller deletes it afterwards. */
    fun decryptToCache(context: Context, attachment: Attachment): File? = try {
        val plain = Crypto.decrypt(File(dir(context), attachment.fileName).readBytes())
        val out = File(context.cacheDir, "play_${System.currentTimeMillis()}.m4a")
        out.writeBytes(plain)
        out
    } catch (e: Exception) {
        Log.e("SheShield", "Could not decrypt audio clip", e)
        null
    }

    /** True if the decrypted clip still matches the fingerprint saved with it. */
    fun verifyAttachment(context: Context, attachment: Attachment): Boolean = try {
        val plain = Crypto.decrypt(File(dir(context), attachment.fileName).readBytes())
        Crypto.sha256Hex(plain) == attachment.sha256
    } catch (e: Exception) {
        false
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        get(context, id)?.incident?.attachments?.forEach {
            File(dir(context), it.fileName).delete()
        }
        fileFor(context, id).delete()
    }

    @Synchronized
    fun get(context: Context, id: String): StoredIncident? = read(fileFor(context, id))

    @Synchronized
    fun list(context: Context): List<StoredIncident> =
        dir(context).listFiles { f -> f.name.startsWith("incident_") && f.name.endsWith(".enc") }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.incident.startedAt }
            ?: emptyList()

    // ---------- internals ----------

    private fun update(context: Context, id: String, change: (Incident) -> Incident) {
        val current = get(context, id)?.incident ?: return
        save(context, change(current))
    }

    private fun save(context: Context, incident: Incident) {
        try {
            val data = toJson(incident)
            val wrapper = JSONObject()
                .put("data", data)
                .put("sha256", Crypto.sha256Hex(data.toByteArray(Charsets.UTF_8)))
                .put("savedAt", System.currentTimeMillis())
            val encrypted = Crypto.encrypt(wrapper.toString().toByteArray(Charsets.UTF_8))
            fileFor(context, incident.id).writeBytes(encrypted)
        } catch (e: Exception) {
            Log.e("SheShield", "Could not save incident", e)
        }
    }

    private fun read(file: File): StoredIncident? = try {
        val plain = String(Crypto.decrypt(file.readBytes()), Charsets.UTF_8)
        val wrapper = JSONObject(plain)
        val data = wrapper.getString("data")
        val storedHash = wrapper.getString("sha256")
        val actualHash = Crypto.sha256Hex(data.toByteArray(Charsets.UTF_8))
        StoredIncident(
            incident = fromJson(JSONObject(data)),
            sha256 = storedHash,
            savedAt = wrapper.getLong("savedAt"),
            verified = storedHash == actualHash
        )
    } catch (e: Exception) {
        Log.e("SheShield", "Could not read incident file", e)
        null
    }

    private fun toJson(i: Incident): String {
        val points = JSONArray()
        i.points.forEach {
            points.put(
                JSONObject()
                    .put("time", it.time)
                    .put("lat", it.lat ?: JSONObject.NULL)
                    .put("lng", it.lng ?: JSONObject.NULL)
            )
        }
        val alerted = JSONArray()
        i.alerted.forEach { alerted.put(it) }
        val attachments = JSONArray()
        i.attachments.forEach {
            attachments.put(
                JSONObject()
                    .put("fileName", it.fileName)
                    .put("label", it.label)
                    .put("sha256", it.sha256)
                    .put("savedAt", it.savedAt)
                    .put("kind", it.kind)
            )
        }
        return JSONObject()
            .put("id", i.id)
            .put("type", i.type)
            .put("startedAt", i.startedAt)
            .put("endedAt", i.endedAt ?: JSONObject.NULL)
            .put("notes", i.notes)
            .put("alerted", alerted)
            .put("points", points)
            .put("attachments", attachments)
            .toString()
    }

    private fun fromJson(o: JSONObject): Incident {
        val points = mutableListOf<LocationPoint>()
        val pointsArr = o.optJSONArray("points")
        if (pointsArr != null) {
            for (i in 0 until pointsArr.length()) {
                val p = pointsArr.getJSONObject(i)
                points.add(
                    LocationPoint(
                        time = p.getLong("time"),
                        lat = if (p.isNull("lat")) null else p.getDouble("lat"),
                        lng = if (p.isNull("lng")) null else p.getDouble("lng")
                    )
                )
            }
        }
        val names = mutableListOf<String>()
        val namesArr = o.optJSONArray("alerted")
        if (namesArr != null) {
            for (i in 0 until namesArr.length()) names.add(namesArr.getString(i))
        }
        val attachments = mutableListOf<Attachment>()
        val attArr = o.optJSONArray("attachments")
        if (attArr != null) {
            for (i in 0 until attArr.length()) {
                val a = attArr.getJSONObject(i)
                attachments.add(
                    Attachment(
                        fileName = a.getString("fileName"),
                        label = a.optString("label", "Audio"),
                        sha256 = a.getString("sha256"),
                        savedAt = a.getLong("savedAt"),
                        kind = a.optString("kind", "audio")
                    )
                )
            }
        }
        return Incident(
            id = o.getString("id"),
            type = o.optString("type", "SOS"),
            startedAt = o.getLong("startedAt"),
            endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
            points = points,
            alerted = names,
            notes = o.optString("notes", ""),
            attachments = attachments
        )
    }
}