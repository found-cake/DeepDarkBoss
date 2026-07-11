package io.github.found_cake.deep_dark_boss

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AncientCityCacheTest {
    @Test
    fun `negative chunk coordinates use floor division`() {
        val worldId = UUID.randomUUID()

        assertEquals(AncientCityRegion(worldId, -1, -1), ancientCityRegion(worldId, -1, -1))
        assertEquals(AncientCityRegion(worldId, -1, -1), ancientCityRegion(worldId, -4, -4))
        assertEquals(AncientCityRegion(worldId, -2, -2), ancientCityRegion(worldId, -5, -5))
    }

    @Test
    fun `repeated lookup in the same region searches once`() {
        val worldId = UUID.randomUUID()
        val cache = AccessOrderLruCache<AncientCityRegion, String>(2_000)
        var searches = 0

        repeat(4) { chunkCoordinate ->
            cache.getOrPut(ancientCityRegion(worldId, chunkCoordinate, chunkCoordinate)) {
                searches += 1
                "found"
            }
        }

        assertEquals(1, searches)
    }

    @Test
    fun `not found result is cached`() {
        val worldId = UUID.randomUUID()
        val cache = AccessOrderLruCache<AncientCityRegion, SearchResult>(2_000)
        var searches = 0

        repeat(4) { chunkCoordinate ->
            cache.getOrPut(ancientCityRegion(worldId, chunkCoordinate, chunkCoordinate)) {
                searches += 1
                SearchResult.NotFound
            }
        }

        assertEquals(1, searches)
    }

    @Test
    fun `least recently used entry is removed above two thousand entries`() {
        val cache = AccessOrderLruCache<Int, Int>(2_000)

        repeat(2_000) { key -> cache.getOrPut(key) { key } }
        cache.getOrPut(0) { error("entry 0 should still be cached") }
        cache.getOrPut(2_000) { 2_000 }

        var evictedEntrySearches = 0
        cache.getOrPut(1) {
            evictedEntrySearches += 1
            1
        }
        cache.getOrPut(0) { error("recently accessed entry 0 must not be evicted") }

        assertEquals(1, evictedEntrySearches)
        assertEquals(2_000, cache.size)
    }

    private sealed interface SearchResult {
        data object NotFound : SearchResult
    }
}
