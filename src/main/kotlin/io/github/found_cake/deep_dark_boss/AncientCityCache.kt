package io.github.found_cake.deep_dark_boss

import java.util.UUID

internal data class AncientCityRegion(
    val worldId: UUID,
    val x: Int,
    val z: Int,
)

internal fun ancientCityRegion(worldId: UUID, chunkX: Int, chunkZ: Int): AncientCityRegion =
    AncientCityRegion(
        worldId = worldId,
        x = Math.floorDiv(chunkX, 4),
        z = Math.floorDiv(chunkZ, 4),
    )

internal class AccessOrderLruCache<K, V>(private val maximumSize: Int) {
    init {
        require(maximumSize > 0) { "maximumSize must be positive" }
    }

    private val entries =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
                size > maximumSize
        }

    val size: Int
        get() = entries.size

    fun getOrPut(key: K, defaultValue: () -> V): V =
        entries.getOrPut(key, defaultValue)
}
