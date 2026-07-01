package io.github.found_cake.deep_dark_boss

import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.damage.DamageType
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack


class EventListener(private val flag: String) : Listener {

    private val cursedEventBook = eventListenerDsl(flag) {
        whenWardenIsDamaged {
            cancelItBecauseWeSaidSo()
        }

        whenWardenDies {
            ifTheKillerIsPlayerAttack {
                awardFlagBookAndSpectatorMode()
            }
        }

        whenPlayerDropsItem {
            makeDropBelongToPlayerAndAge()
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
}

@DeepDarkBossEventDsl
private class EventListenerDsl(private val flag: String) {
    private var damagedWardenScript: EntityDamageByEntityEvent.() -> Unit = {}
    private var deadWardenScript: EntityDeathEvent.() -> Unit = {}
    private var droppedItemScript: PlayerDropItemEvent.() -> Unit = {}

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

    fun build(): EventListenerScript =
        EventListenerScript(
            damagedWardenScript = damagedWardenScript,
            deadWardenScript = deadWardenScript,
            droppedItemScript = droppedItemScript,
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
    private val flag: String,
) {
    private val winnerCeremony: List<EventCurse<Player>> =
        listOf(
            { sendMessage(flag) },
            { inventory.clear() },
            { inventory.addItem(flagBook()) },
            { gameMode = GameMode.SPECTATOR },
        )

    fun awardFlagBookAndSpectatorMode() {
        winnerCeremony.forEach { curse ->
            curse.invoke(player)
        }
    }

    private fun flagBook(): ItemStack =
        ItemStack(Material.BOOK).also { item ->
            item.itemMeta
                .also { meta -> meta.customName(Component.text(flag)) }
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
