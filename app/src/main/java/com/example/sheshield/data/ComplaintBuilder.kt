package com.example.sheshield.data


import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ComplainantInfo(
    val name: String,
    val phone: String,
    val station: String,
    val details: String
)

/** Fills a complaint letter from an incident record. Pure text, no Android UI. */
object ComplaintBuilder {

    private fun stamp(ms: Long): String =
        SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(ms))

    private fun dayOf(ms: Long): String =
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))

    private fun clockOf(ms: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

    private fun coords(lat: Double, lng: Double): String =
        "%.5f, %.5f".format(Locale.US, lat, lng)

    private fun mapLink(lat: Double, lng: Double): String =
        "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lng)

    fun build(
        stored: StoredIncident,
        info: ComplainantInfo,
        now: Long = System.currentTimeMillis()
    ): String {
        val inc = stored.incident
        val name = info.name.trim().ifEmpty { "[Your full name]" }
        val station = info.station.trim().ifEmpty { "[Name of the nearest police station]" }
        val phoneBit = info.phone.trim().let { if (it.isEmpty()) "" else " (phone: $it)" }
        val details = info.details.trim().ifEmpty {
            "[Please describe what happened, who was involved, and anything you remember, " +
                    "such as appearance, vehicle number or the direction they went.]"
        }

        val located = inc.points.mapNotNull { p ->
            val lat = p.lat
            val lng = p.lng
            if (lat != null && lng != null) Triple(p.time, lat, lng) else null
        }
        val clips = inc.attachments.filter { it.kind == "audio" }.sortedBy { it.savedAt }

        val sb = StringBuilder()
        sb.appendLine("To,")
        sb.appendLine("The Station House Officer (SHO)")
        sb.appendLine(station)
        sb.appendLine()
        sb.appendLine("Date: ${dayOf(now)}")
        sb.appendLine()
        sb.appendLine("SUBJECT: Complaint about a safety incident on ${dayOf(inc.startedAt)}")
        sb.appendLine()
        sb.appendLine("Respected Sir/Madam,")
        sb.appendLine()
        sb.appendLine(
            "I, $name$phoneBit, wish to report a safety incident that took place on " +
                    "${dayOf(inc.startedAt)} at about ${clockOf(inc.startedAt)}."
        )
        sb.appendLine()

        sb.appendLine("WHAT HAPPENED")
        sb.append(
            "An emergency (SOS) alert was raised from my phone using the SheShield safety app at " +
                    "${clockOf(inc.startedAt)}"
        )
        if (inc.endedAt != null) {
            sb.appendLine(", and I marked myself safe at ${clockOf(inc.endedAt)}.")
        } else {
            sb.appendLine(". The alert was not marked safe in the app.")
        }
        sb.appendLine(details)
        sb.appendLine()

        sb.appendLine("LOCATION")
        if (located.isEmpty()) {
            sb.appendLine("The phone could not record a location during this incident.")
        } else {
            val first = located.first()
            val last = located.last()
            sb.appendLine("First recorded location (${clockOf(first.first)}): ${coords(first.second, first.third)}")
            sb.appendLine(mapLink(first.second, first.third))
            if (located.size > 1) {
                sb.appendLine("Last recorded location (${clockOf(last.first)}): ${coords(last.second, last.third)}")
                sb.appendLine(mapLink(last.second, last.third))
            }
            sb.appendLine()
            sb.appendLine("All recorded location points (${located.size}):")
            located.take(40).forEachIndexed { i, p ->
                sb.appendLine("${i + 1}. ${stamp(p.first)}   ${coords(p.second, p.third)}")
            }
            if (located.size > 40) sb.appendLine("... and ${located.size - 40} more points in the app.")
        }
        sb.appendLine()

        sb.appendLine("EVIDENCE")
        if (clips.isEmpty()) {
            sb.appendLine("No audio recording was saved for this incident.")
        } else {
            sb.appendLine(
                "${clips.size} audio recording(s) were saved automatically on my phone, encrypted. " +
                        "Each has a SHA-256 fingerprint and a save time, so it can be shown that the file " +
                        "was not changed after it was saved."
            )
            clips.forEachIndexed { i, c ->
                sb.appendLine("${i + 1}. ${c.label}, saved ${stamp(c.savedAt)}")
                sb.appendLine("   SHA-256: ${c.sha256}")
            }
        }
        sb.appendLine()
        sb.appendLine("Incident record saved: ${stamp(stored.savedAt)}")
        sb.appendLine("Incident record SHA-256: ${stored.sha256}")
        sb.appendLine(
            "Integrity check when this letter was made: " +
                    if (stored.verified) "record matches its fingerprint." else "FAILED, the record does not match its fingerprint."
        )
        sb.appendLine()

        sb.appendLine("PEOPLE ALERTED")
        if (inc.alerted.isEmpty()) {
            sb.appendLine("No trusted contacts were recorded as alerted.")
        } else {
            sb.appendLine("My trusted contacts were sent an SMS with my location: ${inc.alerted.joinToString(", ")}.")
        }
        sb.appendLine()

        sb.appendLine("REQUEST")
        sb.appendLine(
            "I request that this complaint be registered and that appropriate action be taken. " +
                    "I can provide the original recordings and the incident record on request."
        )
        sb.appendLine()
        sb.appendLine("Yours faithfully,")
        sb.appendLine(name)
        if (info.phone.isNotBlank()) sb.appendLine("Phone: ${info.phone.trim()}")
        sb.appendLine("Date: ${dayOf(now)}")
        sb.appendLine()
        sb.appendLine("-----")
        sb.appendLine(
            "This letter was generated by the SheShield app on ${stamp(now)} from the records on the " +
                    "complainant's phone. Please review and correct it before submitting. It is a draft to help " +
                    "you file a complaint and is not legal advice."
        )
        return sb.toString().trimEnd()
    }
}