package com.example.sheshield.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScoredRoute(
    val index: Int,
    val route: RouteOption,
    val context: RouteContext?,
    val reports: ReportSummary?,
    val score: Int,          // 0 to 100, higher is safer
    val reasons: List<String>,
    val rank: Int            // 0 = safest
) {
    /** False when the map analysis failed and the score is only a rough fallback. */
    val analysed: Boolean get() = context != null
}

object SafetyScorer {

    /** How much the time of day matters: 0 = daytime, 1 = deep night. */
    private fun nightFactor(hour: Int): Double = when (hour) {
        in 21..23, in 0..4 -> 1.0
        19, 20 -> 0.6
        5 -> 0.4
        18 -> 0.3
        else -> 0.0
    }

    /** How exposed the traveller is, by transport mode. */
    private fun modeRisk(mode: TransportMode): Double = when (mode) {
        TransportMode.CAB -> 0.3
        TransportMode.AUTO -> 0.6
        TransportMode.METRO -> 0.5
        TransportMode.WALKING -> 1.0
    }

    fun scoreAll(
        routes: List<RouteOption>,
        contexts: List<RouteContext?>,
        reports: List<ReportSummary?>,
        hour: Int,
        mode: TransportMode,
        alone: Boolean
    ): List<ScoredRoute> {
        val results = routes.mapIndexed { i, r ->
            compute(r, contexts.getOrNull(i), reports.getOrNull(i), hour, mode, alone)
        }
        val order = routes.indices.sortedByDescending { results[it].first }
        return routes.mapIndexed { i, r ->
            ScoredRoute(
                index = i,
                route = r,
                context = contexts.getOrNull(i),
                reports = reports.getOrNull(i),
                score = results[i].first.roundToInt().coerceIn(0, 100),
                reasons = results[i].second,
                rank = order.indexOf(i)
            )
        }
    }

    private fun compute(
        route: RouteOption,
        c: RouteContext?,
        rep: ReportSummary?,
        hour: Int,
        mode: TransportMode,
        alone: Boolean
    ): Pair<Double, List<String>> {
        val n = nightFactor(hour)
        val exposure = n * modeRisk(mode) * (if (alone) 1.3 else 1.0)
        val flat = exposure * 8.0
        val minutes = route.durationS / 60.0
        val reasons = mutableListOf<String>()
        var s: Double

        if (c == null) {if (n >= 0.6) reasons.add("Late evening or night: score is lowered because fewer people are around")
            reasons.add("Travelling by ${mode.name.lowercase()} is factored into the score")
            if (alone) reasons.add("Travelling alone is factored into the score")
            s = 60.0 - flat - minutes * 0.05
        } else {
            val km = max(c.lengthM / 1000.0, 0.1)
            s = 50.0

            // Road type: main roads are busier and safer, more so at night
            s += c.mainRoadPct * 0.20 * (1 + 0.5 * n)

            // Lighting: only counted when OSM has lighting data (it is sparse in Delhi)
            if (c.litPct > 0) s += c.litPct * 0.15 * (1 + n)

            // Activity nearby: shops and cafes per km, so long routes aren't favoured unfairly
            s += min(c.shopsNearby / km * 1.5, 12.0)

            // Help nearby: police, hospital, metro
            val help = c.police + c.hospitals + c.metroStations
            s += min(help * 3.0, 8.0)

            // Ways to get away or get picked up: bus stops and auto/cab stands
            s += min(c.busStops * 1.0 + c.autoStands * 1.5, 5.0)

            // Isolated stretches hurt more at night, on foot, and when alone
            val isoTotal = c.isolatedStretchesM.sum()
            s -= min(isoTotal / 60.0, 25.0) * (1 + exposure)

            // Time of day and transport mode
            s -= flat

            // Tiny tie-breaker: shorter trips are slightly better
            s -= minutes * 0.05

            if (c.mainRoadPct >= 60) {
                reasons.add("Stays mostly on main roads (${c.mainRoadPct}%)")
            } else if (c.lanePct >= 40) {
                reasons.add("Goes through lanes and side roads for ${c.lanePct}% of the route")
            }
            if (c.litPct >= 40) {
                reasons.add("Street lighting is recorded along ${c.litPct}% of the route")
            } else if (c.litPct > 0) {
                reasons.add("Some street lighting is recorded (${c.litPct}% of the route)")
            } else if (c.mainRoadPct >= 60) {
                reasons.add("Likely well lit: mostly main roads (lighting itself isn't mapped on OpenStreetMap here, so this is an estimate)")
            } else {
                reasons.add("Lighting isn't mapped on OpenStreetMap here, so it is not counted for or against this route")
            }
            if (c.shopsNearby > 0) {
                reasons.add("Passes ${c.shopsNearby} shops or cafes")
            } else {
                reasons.add("No shops or cafes are mapped along this route")
            }
            val near = mutableListOf<String>()
            if (c.police > 0) near.add("${c.police} police station" + if (c.police > 1) "s" else "")
            if (c.hospitals > 0) near.add("${c.hospitals} hospital" + if (c.hospitals > 1) "s" else "")
            if (c.metroStations > 0) near.add("${c.metroStations} metro station" + if (c.metroStations > 1) "s" else "")
            if (near.isNotEmpty()) reasons.add("Within 500 m of: " + near.joinToString(", "))

            val transport = mutableListOf<String>()
            if (c.busStops > 0) transport.add("${c.busStops} bus stop" + if (c.busStops > 1) "s" else "")
            if (c.autoStands > 0) transport.add("${c.autoStands} auto/cab stand" + if (c.autoStands > 1) "s" else "")
            if (transport.isNotEmpty()) reasons.add("Transport nearby: " + transport.joinToString(", "))

            if (c.longestIsolatedM >= 300) {
                reasons.add("Has a ${c.longestIsolatedM} m stretch with no shops nearby")
            } else {
                reasons.add("No long isolated stretches")
            }
        }

        // Community reports (only counted once the minimum number of reports is reached)
        if (rep != null) {
            if (rep.meetsMinimum) {
                s -= min(rep.penalty * 1.5, 25.0)
                reasons.add("${rep.negativeTotal} community discomfort reports near this route around ${bucketLabel(rep.bucket)}")
            }
            if (rep.feltSafe >= ReportStore.MIN_REPORTS) {
                s += min(rep.feltSafe * 1.5, 6.0)
                reasons.add("${rep.feltSafe} reports of feeling safe near this route around this time")
            }
        }

        if (n >= 0.6 && mode == TransportMode.WALKING) {
            reasons.add("Walking late in the evening or at night is penalised more")
        }
        if (n >= 0.6 && alone) {
            reasons.add("Travelling alone late counts extra against quiet stretches")
        }

        return s to reasons
    }
}