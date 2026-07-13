package io.github.found_cake.deep_dark_boss

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.TextComponent

fun TextComponent.Builder.text(
    content: String,
    color: NamedTextColor,
    bold: Boolean = false
): TextComponent.Builder {
    val component = Component.text(content, color).let {
        if (bold) it.decorate(TextDecoration.BOLD) else it
    }

    return append(component)
}