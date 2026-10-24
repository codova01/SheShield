package com.example.sheshield.data

data class DemoPlace(
    val name: String,
    val area: String,
    val lat: Double,
    val lng: Double
)

object DemoPlaces {
    // Demo list of popular Delhi places. Coordinates are approximate.
    val all = listOf(
        DemoPlace("Hauz Khas Village", "South Delhi", 28.5535, 77.1943),
        DemoPlace("Connaught Place", "Central Delhi", 28.6315, 77.2167),
        DemoPlace("India Gate", "Central Delhi", 28.6129, 77.2295),
        DemoPlace("Select Citywalk, Saket", "South Delhi", 28.5286, 77.2190),
        DemoPlace("Sarojini Nagar Market", "South West Delhi", 28.5753, 77.1993),
        DemoPlace("Lajpat Nagar Central Market", "South East Delhi", 28.5677, 77.2433),
        DemoPlace("Nehru Place", "South Delhi", 28.5492, 77.2518),
        DemoPlace("Green Park Metro Station", "South Delhi", 28.5586, 77.2062),
        DemoPlace("AIIMS", "South Delhi", 28.5672, 77.2100),
        DemoPlace("New Delhi Railway Station", "Central Delhi", 28.6428, 77.2197),
        DemoPlace("Chandni Chowk", "Old Delhi", 28.6506, 77.2303),
        DemoPlace("Karol Bagh", "Central Delhi", 28.6519, 77.1900),
        DemoPlace("Vasant Kunj", "South West Delhi", 28.5410, 77.1550),
        DemoPlace("IGI Airport Terminal 3", "West Delhi", 28.5562, 77.1000)
    )

    fun search(query: String): List<DemoPlace> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all.take(5)
        return all.filter {
            it.name.lowercase().contains(q) || it.area.lowercase().contains(q)
        }.take(6)
    }
}
