package io.github.found_cake.deep_dark_boss

import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Loader: JavaPlugin() {

    override fun onEnable() {
        dataFolder.mkdirs()
        this.logger.info("flag 생성중...")
        val flagFile = File(dataFolder, "flag.txt")
        if (!flagFile.exists()) flagFile.createNewFile()
        val flag = flagFile.readText(Charsets.UTF_8)

        this.server.pluginManager.registerEvents(EventListener(flag), this)
    }
}