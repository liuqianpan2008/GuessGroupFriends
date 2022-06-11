package org.fenglin

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSender
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.UserProfile
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object Command : CompositeCommand(
    GuessGroupFriends,
    "猜群友",
    "猜群友游戏",
    description = "开始猜群友！"
) {
    /**
     * 正在进行的游戏 <群号, 游戏>
     */
    private val games = ConcurrentHashMap<Long, Game>()

    // 最近24小时发言过的
    private const val lastSpeakLimit = 24 * 60 * 60 * 1000L
    // 缓存有效期10分钟
    private const val profileCacheTimeout = 600_000
    private val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")

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
     * @param member 正在猜的群友
     */
    class Game(val group: Group, val member: NormalMember) {
        private val profile by lazy { runBlocking { member.getCacheProfile() } }
        private val hint = arrayListOf(
            { PlainText("ta的网名中含有${member.nameCardOrNick.random()}") },
            { PlainText("ta的入群时间为:${sdf.format(Date(member.joinTimestamp * 1000L))}") },
            { PlainText("ta的最后发言时间:${sdf.format(Date(member.lastSpeakTimestamp * 1000L))}") },
            { PlainText("ta的性别:${profile.sex.alias()}") },
            { PlainText("ta的QQ等级:${profile.qLevel}") },
            { PlainText("ta的个性签名:${profile.sign}") },
            { PlainText("ta的年龄:${profile.age}") },
        )

        fun getHint() =
            if (hint.isEmpty()) null
            else hint.removeAt(Random.nextInt(hint.size)).invoke()

        suspend fun start() {
            group.sendMessage(
                """游戏开始，下面将发送第一条线索
                    |如果回答错误，将随机发送一条线索，线索用完时游戏失败
                    |发送 `/猜群友 猜 @群友` 进行游戏
                """.trimMargin()
            )
            group.sendMessage(getHint()!!)
        }

        fun end() {
            games.remove(group.id)
        }
    }

    // 开始游戏
    @SubCommand("开始")
    @Description("开始猜群友游戏")
    suspend fun CommandSender.play() {
        if (this !is MemberCommandSender) {
            sendMessage("仅可在群聊中使用")
            return
        }
        if (games[group.id] != null) {
            sendMessage("游戏进行中, 发送 `/猜群友 猜 @猜的群友` 来猜群友")
            return
        }
        // 获取群友
        val member = group.members.filter { member ->
            conditions.all { it.invoke(member) }
        }[Random.nextInt(group.members.size)]
        val game = Game(group, member)
        game.start()
    }

    // 猜群友
    @SubCommand("猜")
    @Description("在猜群友游戏猜一次群友")
    suspend fun CommandSender.guess(target: User, chain: MessageChain) {
        if (this !is MemberCommandSender) {
            sendMessage("仅可在群聊中使用")
            return
        }
        val game = games[group.id]
        if (game == null) {
            sendMessage("游戏未开始！")
            return
        }
        val success = target.id == game.member.id
        if (success) {
            group.sendMessage(buildMessageChain {
                append(PlainText("恭喜 "))
                append(At(user))
                append(PlainText(" 猜出了群友, 游戏结束\n发送 `/猜群友 开始` 开始新游戏"))
            })
            return
        }
        val hint = game.getHint()
        // 提示用完
        if (hint == null) {
            game.end()
            sendMessage("看来没人能猜到我，我要公布答案了：这位群友是: ${game.member.nameCardOrNick}")
            return
        }
        sendMessage(
            buildMessageChain {
                append(chain.quote())
                append(At(user))
                append("回答错误！\n新提示：$hint")
            }
        )
    }
}