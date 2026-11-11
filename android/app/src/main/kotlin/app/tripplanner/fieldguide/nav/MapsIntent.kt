package app.tripplanner.fieldguide.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens the place in Google Maps. With a place id the app shows the business sheet; the coordinates in
 * `query` still pin the spot when the sheet cannot load offline. No location permission is involved.
 */
fun openInGoogleMaps(context: Context, lat: Double, lng: Double, placeId: String?, label: String) {
    val search = Uri.parse("https://www.google.com/maps/search/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("query", "$lat,$lng")
        .apply { if (!placeId.isNullOrBlank()) appendQueryParameter("query_place_id", placeId) }
        .build()

    val maps = Intent(Intent.ACTION_VIEW, search)
        .setPackage("com.google.android.apps.maps")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(maps)
        return
    } catch (_: ActivityNotFoundException) {
        // Google Maps is missing or disabled. Fall through to any map app.
    }

    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(geo)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
    }
}
