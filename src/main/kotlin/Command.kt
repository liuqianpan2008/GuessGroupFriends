package org.fenglin

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.UserProfile
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import org.jetbrains.skia.EncodedImageFormat
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timerTask
import kotlin.random.Random

object Command : CompositeCommand(
    GuessGroupFriends,
    "猜群友",
    "猜群友游戏",
    description = "开始猜群友"
) {
    /**
     * 正在进行的游戏 <群号, 游戏>
     */
    private val games = ConcurrentHashMap<Long, Game>()

    // 最近24小时发言过的
    private const val lastSpeakLimit = 24 * 60 * 60 * 1000L

    // 缓存有效期10分钟
    private const val profileCacheTimeout = 600_000

    /**
     * 用户UserProfile缓存 <id, <profile, 缓存时间戳>>
     */
    private val profileCache = ConcurrentHashMap<Long, Pair<UserProfile, Long>>()
    suspend fun Member.getCacheProfile(): UserProfile {
        val pair = profileCache[id]
        if (pair != null
            && pair.second > System.currentTimeMillis() - profileCacheTimeout
        ) return pair.first
        // 缓存过期
        return queryProfile().also {
            profileCache[id] = Pair(it, System.currentTimeMillis())
        }
    }

    private fun UserProfile.Sex.alias() = when (this) {
        UserProfile.Sex.MALE -> "男"
        UserProfile.Sex.FEMALE -> "女"
        UserProfile.Sex.UNKNOWN -> "未知"
    }

    private val conditions = mutableListOf<NormalMember.() -> Boolean>(
        { lastSpeakTimestamp * 1000L + lastSpeakLimit >= System.currentTimeMillis() }
    )

    /**
     * 代表一个群里正在进行的游戏
     *
     * @param group 群
     * @param member 正在猜的群员
     * @param owner 创建游戏的群员id
     */
    class Game(val group: Group, val member: NormalMember, val owner: User) {
        companion object {
            private val timer = Timer("GuessGroupFriends")

            // 5分钟无响应即为超时
            private const val timeout = 5 * 60 * 1000L
        }

        private var task: TimerTask? = null
        val face by lazy { URL(member.avatarUrl).readBytes().toImage() }

        private val profile = GuessGroupFriends.async(start = CoroutineStart.LAZY) {
            member.getCacheProfile()
        }
        private suspend fun getProfile() = profile.await()

        val hint = arrayListOf<suspend () -> Message>(
            { PlainText("ta的昵称中包含字符: ${member.nameCardOrNick.random()}") },
            { PlainText("ta的群头衔: ${member.specialTitle}") },
            { PlainText("ta${if (member.permission == MemberPermission.ADMINISTRATOR) "是" else "不是"}群管理") },
            { PlainText("ta的入群时间为: ${member.joinTimestamp.formatAsDate()}(${member.joinTimestamp.fromNowSecondly()}前)") },
            { PlainText("ta的最后发言时间: ${member.lastSpeakTimestamp.formatAsDate()}(${member.lastSpeakTimestamp.fromNowSecondly()}前)") },
            { PlainText("ta的性别: ${getProfile().sex.alias()}") },
            { PlainText("ta的QQ等级: ${getProfile().qLevel}") },
            { PlainText(if (getProfile().sign.isEmpty()) "ta的个性签名是空的" else "ta的个性签名: ${getProfile().sign}") },
            { PlainText("ta的年龄: ${getProfile().age}") },
            { PlainText("ta的头像包含以下部分\n").plus(group.uploadImage(face.split())) },
            { PlainText("ta的头像模糊之后是这样的\n").plus(group.uploadImage(face.blur())) },
            { PlainText("ta的头像缩放之后是这样的\n").plus(group.uploadImage(face.scale())) },
        )

        suspend fun getHint() =
            if (hint.isEmpty()) null
            else hint.removeAt(Random.nextInt(hint.size)).invoke()

        fun updateTask() {
            task?.cancel()
            task = timerTask {
                runBlocking {
                    PlainText("由于长时间无人触发, ")
                        .plus(At(owner))
                        .plus(" 创建的游戏已自定关闭\n发送 `/猜群友 开始` 开始新游戏")
                        .sendTo(group)
                }
                games.remove(group.id)
            }
            timer.schedule(task, timeout)
        }

        suspend fun start() {
            games[group.id] = this
            GuessGroupFriends.logger.info("在群${group.name}(${group.id})开始猜群友游戏, 选择的群员: ${member.nameCardOrNick}(${member.id})")
            group.sendMessage(
                """游戏开始, 下面将发送第一条线索
                    |如果回答错误, 将随机发送一条线索, 线索用完时游戏失败
                    |发送 `/猜群友 猜 @群友` 进行游戏
                """.trimMargin()
            )
            updateTask()
            group.sendMessage(getHint()!!)
        }

        fun end() {
            task?.cancel()
            games.remove(group.id)
        }
    }

    @SubCommand("开始")
    @Description("开始猜群友游戏")
    suspend fun CommandSender.play() {
        if (this !is MemberCommandSenderOnMessage) {
            sendMessage("仅可在群聊中使用")
            return
        }
        if (games[group.id] != null) {
            sendMessage(
                fromEvent.source.quote().plus(
                    """游戏进行中
                    |发送 `/猜群友 猜 @猜的群友` 猜群友
                    |发送 `/猜群友 结束` 停止当前游戏
                """.trimMargin()
                )
            )
            return
        }
        // 获取群友
        val filter = group.members.filter { member ->
            conditions.all { it.invoke(member) }
        }
        if (filter.isEmpty()) {
            sendMessage(fromEvent.source.quote().plus("没有满足条件的群员"))
            return
        }
        val member = filter[Random.nextInt(filter.size)]
        Game(group, member, user).start()
    }

    @SubCommand("猜")
    @Description("在猜群友游戏猜一次群友")
    suspend fun CommandSender.guess(target: User) {
        if (this !is MemberCommandSenderOnMessage) {
            sendMessage("仅可在群聊中使用")
            return
        }
        val game = games[group.id]
        if (game == null) {
            sendMessage(fromEvent.source.quote().plus("游戏未开始\n发送 `/猜群友 开始` 开始游戏"))
            return
        }
        val success = target.id == game.member.id
        if (success) {
            game.end()
            group.sendMessage(buildMessageChain {
                append(PlainText("恭喜 "))
                append(At(user))
                append(PlainText(" 猜出了群友, 游戏结束\n"))
                val face = game.face
                    .encodeToData(EncodedImageFormat.PNG)!!
                    .bytes
                    .toExternalResource()
                    .use { it.uploadAsImage(group) }
                append(face)
                append(PlainText("\n发送 `/猜群友 开始` 开始新游戏"))
            })
            return
        }
        val hint = game.getHint()
        // 提示用完
        if (hint == null) {
            game.end()
            sendMessage("没有人猜出来, 游戏结束: 这位群友是: ${game.member.nameCardOrNick}")
            return
        }
        game.updateTask()
        sendMessage(fromEvent.source.quote().plus("回答错误\n新提示: ").plus(hint))
    }

    @SubCommand("结束")
    @Description("结束猜群友游戏")
    suspend fun CommandSender.stop() {
        if (this !is MemberCommandSenderOnMessage) {
            sendMessage("仅可在群聊中使用")
            return
        }
        val game = games[group.id]
        if (game == null) {
            sendMessage(fromEvent.source.quote().plus("游戏未开始\n发送 `/猜群友 开始` 开始游戏"))
            return
        }
        if (!hasPermission(GuessGroupFriends.stopPerm)
            || game.owner.id != user.id
        ) {
            sendMessage(fromEvent.source.quote().plus("仅游戏发起者和管理可以停止游戏"))
            return
        }
        game.end()
        sendMessage(
            fromEvent.source.quote()
                .plus("由 ")
                .plus(At(game.owner))
                .plus(" 创建的游戏已被手动停止\n发送 `/猜群友 开始` 开始新游戏")
        )
    }

    @SubCommand("测试")
    @Description("测试线索")
    suspend fun CommandSender.test(target: User) {
        if (this !is MemberCommandSenderOnMessage) {
            sendMessage("仅可在群聊中使用")
            return
        }
        if (!hasPermission(GuessGroupFriends.testPerm)) {
            sendMessage(fromEvent.source.quote().plus("无权限"))
            return
        }
        val game = Game(group, target as NormalMember, user)
        val f = buildForwardMessage(group) {
            withContext(Dispatchers.IO) {
                game.hint.map { func ->
                    async { add(bot, func.invoke()) }
                }.awaitAll()
            }
        }
        group.sendMessage(f)
    }
}