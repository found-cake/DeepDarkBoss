package io.github.found_cake.deep_dark_boss

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.damage.DamageType
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta

private const val ANCIENT_CITY_SEARCH_RADIUS_CHUNKS = 256
private const val ANCIENT_CITY_CACHE_MAXIMUM_SIZE = 2_000
private const val ANCIENT_CITY_NOT_FOUND_MESSAGE = "심연의 메아리가 탐지되지 않았습니다"

private sealed interface AncientCitySearchResult {
    data class Found(val location: Location) : AncientCitySearchResult

    data object NotFound : AncientCitySearchResult
}

private val ancientCitySearchCache =
    AccessOrderLruCache<AncientCityRegion, AncientCitySearchResult>(ANCIENT_CITY_CACHE_MAXIMUM_SIZE)

class EventListener(flag: String) : Listener {

    private val cursedEventBook = eventListenerDsl(flag) {
        whenWardenIsDamaged {
            cancelItBecauseWeSaidSo()
        }

        whenWardenDies {
            ifTheKillerIsPlayerAttack {
                awardFlagAndEnterSpectatorMode()
            }
        }

        whenPlayerDropsItem {
            makeDropBelongToPlayerAndAge()
        }

        whenFoodLevelChanges {
            cancelAndFillFoodLevel()
        }

        whenPlayerHoldsCompass {
            pointAtNearestAncientCity()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamagedWarden(event: EntityDamageByEntityEvent) {
        cursedEventBook(event)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeathWarden(event: EntityDeathEvent) {
        cursedEventBook(event)
    }

    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        cursedEventBook(event)
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        cursedEventBook(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        cursedEventBook(event)
    }
}

@DslMarker
private annotation class DeepDarkBossEventDsl

private typealias EventGate<T> = T.() -> Boolean
private typealias EventCurse<T> = T.() -> Unit

private fun eventListenerDsl(flag: String, block: EventListenerDsl.() -> Unit): EventListenerScript =
    EventListenerDsl(flag).apply(block).build()

private infix fun <T> EventGate<T>.thenDo(curse: EventCurse<T>): EventCurse<T> =
    {
        if (this@thenDo.invoke(this)) {
            curse.invoke(this)
        }
    }

private class EventListenerScript(
    private val damagedWardenScript: EntityDamageByEntityEvent.() -> Unit,
    private val deadWardenScript: EntityDeathEvent.() -> Unit,
    private val droppedItemScript: PlayerDropItemEvent.() -> Unit,
    private val changedFoodLevelScript: FoodLevelChangeEvent.() -> Unit,
    private val heldCompassScript: PlayerItemHeldEvent.() -> Unit,
) {
    operator fun invoke(event: EntityDamageByEntityEvent) {
        event.damagedWardenScript()
    }

    operator fun invoke(event: EntityDeathEvent) {
        event.deadWardenScript()
    }

    operator fun invoke(event: PlayerDropItemEvent) {
        event.droppedItemScript()
    }

    operator fun invoke(event: FoodLevelChangeEvent) {
        event.changedFoodLevelScript()
    }

    operator fun invoke(event: PlayerItemHeldEvent) {
        event.heldCompassScript()
    }
}

@DeepDarkBossEventDsl
private class EventListenerDsl(private val flag: String) {
    private var damagedWardenScript: EntityDamageByEntityEvent.() -> Unit = {}
    private var deadWardenScript: EntityDeathEvent.() -> Unit = {}
    private var droppedItemScript: PlayerDropItemEvent.() -> Unit = {}
    private var changedFoodLevelScript: FoodLevelChangeEvent.() -> Unit = {}
    private var heldCompassScript: PlayerItemHeldEvent.() -> Unit = {}

    fun whenWardenIsDamaged(block: DamagedWardenDsl.() -> Unit) {
        val wardenGate: EventGate<EntityDamageByEntityEvent> = { entity.type === EntityType.WARDEN }
        val wardenCurse: EventCurse<EntityDamageByEntityEvent> = { DamagedWardenDsl(this).block() }

        damagedWardenScript = wardenGate thenDo wardenCurse
    }

    fun whenWardenDies(block: DeadWardenDsl.() -> Unit) {
        val wardenGate: EventGate<EntityDeathEvent> = { entity.type === EntityType.WARDEN }
        val wardenCurse: EventCurse<EntityDeathEvent> = { DeadWardenDsl(this, flag).block() }

        deadWardenScript = wardenGate thenDo wardenCurse
    }

    fun whenPlayerDropsItem(block: DroppedItemDsl.() -> Unit) {
        val anyDropCanBeCursed: EventGate<PlayerDropItemEvent> = { true }
        val droppedItemCurse: EventCurse<PlayerDropItemEvent> = { DroppedItemDsl(this).block() }

        droppedItemScript = anyDropCanBeCursed thenDo droppedItemCurse
    }

    fun whenFoodLevelChanges(block: ChangedFoodLevelDsl.() -> Unit) {
        val anyFoodLevelChangeCanBeCursed: EventGate<FoodLevelChangeEvent> = { true }
        val changedFoodLevelCurse: EventCurse<FoodLevelChangeEvent> = { ChangedFoodLevelDsl(this).block() }

        changedFoodLevelScript = anyFoodLevelChangeCanBeCursed thenDo changedFoodLevelCurse
    }

    fun whenPlayerHoldsCompass(block: HeldCompassDsl.() -> Unit) {
        val compassGate: EventGate<PlayerItemHeldEvent> = {
            player.inventory.getItem(newSlot)?.type === Material.COMPASS
        }
        val heldCompassCurse: EventCurse<PlayerItemHeldEvent> = { HeldCompassDsl(this).block() }

        heldCompassScript = compassGate thenDo heldCompassCurse
    }

    fun build(): EventListenerScript =
        EventListenerScript(
            damagedWardenScript = damagedWardenScript,
            deadWardenScript = deadWardenScript,
            droppedItemScript = droppedItemScript,
            changedFoodLevelScript = changedFoodLevelScript,
            heldCompassScript = heldCompassScript,
        )
}

@DeepDarkBossEventDsl
private class DamagedWardenDsl(private val event: EntityDamageByEntityEvent) {
    fun cancelItBecauseWeSaidSo() {
        event.isCancelled = true
    }
}

@DeepDarkBossEventDsl
private class DeadWardenDsl(
    private val event: EntityDeathEvent,
    private val flag: String,
) {
    fun ifTheKillerIsPlayerAttack(block: FlagWinnerDsl.() -> Unit) {
        val playerAttackGate: EventGate<EntityDeathEvent> = {
            damageSource.damageType === DamageType.PLAYER_ATTACK
        }
        val playerMaybe: EntityDeathEvent.() -> Player? = {
            damageSource.causingEntity as? Player
        }
        val winnerCurse: EventCurse<Player> = {
            FlagWinnerDsl(this, flag).block()
        }

        event
            .takeIf { playerAttackGate.invoke(it) }
            ?.playerMaybe()
            ?.takeIf { it.type === EntityType.PLAYER }
            ?.let { winnerCurse.invoke(it) }
    }
}

@DeepDarkBossEventDsl
private class FlagWinnerDsl(
    private val player: Player,
    flag: String,
) {
    private val flagText = Component.text(flag)
        .color(NamedTextColor.GOLD)
        .decoration(TextDecoration.ITALIC, false)

    private val winnerCeremony: List<EventCurse<Player>> =
        listOf(
            { sendMessage(flagText) },
            { inventory.clear() },
            { inventory.addItem(flagItem()) },
            { gameMode = GameMode.SPECTATOR },
            { server.broadcast(Component.text()
                .text("✦ ", NamedTextColor.GOLD, bold = true)
                .text(name, NamedTextColor.AQUA, bold = true)
                .text("님이 ", NamedTextColor.GRAY)
                .text("FLAG", NamedTextColor.YELLOW, bold = true)
                .text("를 획득하였습니다!", NamedTextColor.WHITE)
                .text(" ✦", NamedTextColor.GOLD, bold = true)
                .build())},
        )

    fun awardFlagAndEnterSpectatorMode() {
        winnerCeremony.forEach { curse ->
            curse.invoke(player)
        }
    }

    private fun flagItem(): ItemStack =
        ItemStack(Material.GOLDEN_APPLE).also { item ->
            item.itemMeta
                .also { meta ->
                    meta.customName(flagText)
                }
                .let(item::setItemMeta)
        }
}

@DeepDarkBossEventDsl
private class DroppedItemDsl(private val event: PlayerDropItemEvent) {
    private val dropCurses: List<EventCurse<PlayerDropItemEvent>> =
        listOf(
            { itemDrop.owner = player.uniqueId },
            { itemDrop.setWillAge(true) },
        )

    fun makeDropBelongToPlayerAndAge() {
        dropCurses.forEach { curse ->
            curse.invoke(event)
        }
    }
}

@DeepDarkBossEventDsl
private class ChangedFoodLevelDsl(private val event: FoodLevelChangeEvent) {
    fun cancelAndFillFoodLevel() {
        event.isCancelled = true
        event.foodLevel = 20
    }
}

@DeepDarkBossEventDsl
private class HeldCompassDsl(private val event: PlayerItemHeldEvent) {
    fun pointAtNearestAncientCity() {
        event.player.let { player ->
            player.inventory.getItem(event.newSlot)?.let { compass ->
                val region = ancientCityRegion(player.world.uid, player.chunk.x, player.chunk.z)
                val searchResult = ancientCitySearchCache.getOrPut(region) {
                    player.world.locateNearestStructure(
                        player.location,
                        Structure.ANCIENT_CITY,
                        ANCIENT_CITY_SEARCH_RADIUS_CHUNKS,
                        false,
                    )?.location
                        ?.let(AncientCitySearchResult::Found)
                        ?: AncientCitySearchResult.NotFound
                }

                when (searchResult) {
                    is AncientCitySearchResult.Found -> compass.pointAt(searchResult.location)
                    AncientCitySearchResult.NotFound -> {
                        compass.clearLodestone()
                        player.sendActionBar(Component.text(ANCIENT_CITY_NOT_FOUND_MESSAGE))
                    }
                }
            }
        }
    }

    private fun ItemStack.pointAt(location: Location) {
        editMeta(CompassMeta::class.java) { meta ->
            meta.lodestone = location
            meta.isLodestoneTracked = false
        }
    }

    private fun ItemStack.clearLodestone() {
        editMeta(CompassMeta::class.java) { meta ->
            meta.lodestone = null
            meta.isLodestoneTracked = false
        }
    }
}
