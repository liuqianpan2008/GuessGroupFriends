package org.fenglin

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.permission.Permission
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.plugin.description.PluginDependency
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info

object GuessGroupFriends : KotlinPlugin(
    JvmPluginDescription(
        id = "org.fenglin.GuessGroupFriends",
        name = "GuessGroupFriends",
        version = "1.0-SNAPSHOT",
    ) {
        author("枫叶秋林(大佬)")
        dependsOn(
            PluginDependency(
                id = "xyz.cssxsh.mirai.plugin.mirai-skia-plugin",
                versionRequirement = ">=1.1.0",
                isOptional = false
            )
        )
    }
) {
    lateinit var stopPerm: Permission
    lateinit var testPerm: Permission


    override fun onEnable() {
        CommandManager.registerCommand(Command)
        stopPerm = PermissionService.INSTANCE.register(
            id = permissionId("command.停止猜群友"),
            description = "允许使用指令手动停止猜群友游戏",
            parent = parentPermission
        )
        testPerm = PermissionService.INSTANCE.register(
            id = permissionId("command.测试猜群友效果"),
            description = "允许使用指令手动测试猜群友效果",
            parent = parentPermission
        )
        logger.info { "猜群友插件加载成功！" }
    }
}