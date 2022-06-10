package org.fenglin

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info

object GuessGroupFriends : KotlinPlugin(
    JvmPluginDescription(
        id = "org.fenglin.GuessGroupFriends",
        name = "GuessGroupFriends",
        version = "1.0-SNAPSHOT",
    ) {
        author("枫叶秋林")
    }
) {
    override fun onEnable() {
        CommandManager.registerCommand(Command)
        logger.info { "猜群友插件加载成功！" }

    }
}