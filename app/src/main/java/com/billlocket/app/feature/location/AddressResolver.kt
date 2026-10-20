package com.billlocket.app.feature.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.billlocket.app.domain.model.GeoPoint
import com.billlocket.app.locale.resolveAppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ResolvedAddress(
    val shortAddress: String,
    val fullAddress: String,
    val detailLocation: String? = null,
    val isFallback: Boolean = false,
)

interface AddressResolver {
    suspend fun resolve(
        point: GeoPoint,
        existingPlaceName: String? = null,
    ): ResolvedAddress
}

@Singleton
class AndroidAddressResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : AddressResolver {
    private val cache = ConcurrentHashMap<String, ResolvedAddress>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ResolvedAddress>>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun resolve(
        point: GeoPoint,
        existingPlaceName: String?,
    ): ResolvedAddress {
        savedPlaceNameAddress(existingPlaceName)?.let { return it }

        // Approximate to 4 decimal places (~11 meters) to deduplicate near-identical coordinates,
        // and include custom place name if provided to avoid cross-cache contamination.
        val placeKey = existingPlaceName?.trim()?.takeIf { it.isNotEmpty() && !isCoordinateString(it) }?.let { ":$it" } ?: ""
        val cacheKey = String.format(Locale.US, "%.4f,%.4f%s", point.latitude, point.longitude, placeKey)
        cache[cacheKey]?.let { return it }

        val deferred = mutex.withLock {
            cache[cacheKey]?.let { return it }
            inFlight.getOrPut(cacheKey) {
                scope.async {
                    val resolved = performGeocode(point, existingPlaceName)
                    if (!resolved.isFallback) {
                        cache[cacheKey] = resolved
                    }
                    mutex.withLock { inFlight.remove(cacheKey) }
                    resolved
                }
            }
        }
        return deferred.await()
    }

    private suspend fun performGeocode(point: GeoPoint, existingPlaceName: String?): ResolvedAddress {
        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext fallback(point, existingPlaceName)
                }
                val geocoder = Geocoder(context, resolveAppLocale(context))
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            point.latitude,
                            point.longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(results: MutableList<Address>) {
                                    continuation.resume(results)
                                }
                                override fun onError(errorMessage: String?) {
                                    continuation.resume(null)
                                }
                            },
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.latitude, point.longitude, 1)
                }

                val address = addresses?.firstOrNull() ?: return@withContext fallback(point, existingPlaceName)
                formatAddress(point, address, existingPlaceName)
            } catch (_: Exception) {
                fallback(point, existingPlaceName)
            }
        }
    }

    private fun formatAddress(point: GeoPoint, address: Address, existingPlaceName: String?): ResolvedAddress {
        val street = address.thoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val number = address.subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val streetLine = when {
            street != null && number != null -> {
                if (street.startsWith(number) || street.contains(number)) street
                else "$number $street"
            }
            street != null -> street
            else -> null
        }
        val ward = address.subLocality?.trim()?.takeIf { it.isNotEmpty() }
        val district = address.subAdminArea?.trim()?.takeIf { it.isNotEmpty() }
        val city = (address.adminArea ?: address.locality)?.trim()?.takeIf { it.isNotEmpty() }
        val feature = address.featureName?.trim()?.takeIf { it.isNotEmpty() }
        val cleanedLine0 = address.getAddressLine(0)
            ?.trim()
            ?.replace(Regex(",?\\s*(Việt Nam|Vietnam)\\s*$", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex(",?\\s*\\b\\d{5,6}\\b"), "")
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        // Cleaned up address components
        val parts = listOfNotNull(streetLine, ward, district, city).distinct()

        val fullAddress = if (parts.size >= 2) {
            parts.joinToString(", ")
        } else {
            if (!cleanedLine0.isNullOrBlank()) {
                cleanedLine0
            } else if (parts.isNotEmpty()) {
                parts.joinToString(", ")
            } else {
                fallback(point, existingPlaceName).fullAddress
            }
        }

        if (!hasLocalAddressDetail(streetLine, ward, district, feature, city, address.countryName, cleanedLine0)) {
            return cityLevelAddress(point, fullAddress)
        }

        // Detailed location (e.g. "Gần Khu đô thị Ngoại Giao Đoàn")
        val detailLocation = when {
            existingPlaceName != null && existingPlaceName.isNotBlank() && !isCoordinateString(existingPlaceName) -> {
                existingPlaceName
            }
            feature != null && feature != street && feature != number && feature != ward && feature != district && !feature.matches(Regex("^[0-9\\-\\s]+$")) -> {
                "Gần $feature"
            }
            ward != null && district != null && streetLine == null -> {
                "Khu vực $ward, $district"
            }
            else -> null
        }

        return ResolvedAddress(
            shortAddress = fullAddress,
            fullAddress = fullAddress,
            detailLocation = detailLocation,
            isFallback = false,
        )
    }

    private fun fallback(point: GeoPoint, existingPlaceName: String?): ResolvedAddress {
        savedPlaceNameAddress(existingPlaceName)?.let { return it }
        val coord = "${point.latitude.formatCoordinate()}, ${point.longitude.formatCoordinate()}"
        return ResolvedAddress(
            shortAddress = "Không xác định được địa chỉ",
            fullAddress = "Không xác định được địa chỉ",
            detailLocation = coord,
            isFallback = true,
        )
    }

    private fun isCoordinateString(text: String): Boolean =
        text.matches(Regex("^[\\d\\.\\,\\-\\s]+$"))

    private fun Double.formatCoordinate(): String =
        String.format(Locale.US, "%.4f", this)
}

internal fun savedPlaceNameAddress(existingPlaceName: String?): ResolvedAddress? {
    val saved = existingPlaceName
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.matches(Regex("^[\\d\\.\\,\\-\\s]+$")) }
        ?: return null
    return ResolvedAddress(
        shortAddress = saved,
        fullAddress = saved,
        detailLocation = null,
        isFallback = false,
    )
}

internal fun hasLocalAddressDetail(
    streetLine: String?,
    ward: String?,
    district: String?,
    feature: String?,
    city: String?,
    country: String?,
    cleanedLine0: String?,
): Boolean {
    if (streetLine != null || ward != null || district != null) return true
    val genericNames = listOfNotNull(city, country).map { it.trim().lowercase(Locale.ROOT) }.toSet()
    val detailedFeature = feature?.trim()?.lowercase(Locale.ROOT)?.takeIf { it !in genericNames }
    val detailedLine = cleanedLine0?.trim()?.lowercase(Locale.ROOT)?.takeIf { it !in genericNames }
    return detailedFeature != null || detailedLine != null
}

internal fun cityLevelAddress(point: GeoPoint, label: String): ResolvedAddress {
    val normalizedLabel = label.trim().ifEmpty { "Không xác định được địa chỉ" }
    val coordinates = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
    return ResolvedAddress(
        shortAddress = "$normalizedLabel • $coordinates",
        fullAddress = normalizedLabel,
        detailLocation = coordinates,
        isFallback = true,
    )
}
