package org.fenglin

import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.contact.NormalMember
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.data.UserProfile
import net.mamoe.mirai.message.data.At
import java.text.SimpleDateFormat
import java.util.*


object Command : CompositeCommand(
    GuessGroupFriends,
    "猜群友",
    "猜群友游戏",
    description = "开始猜群友！"
) {
    private data class Data4Group(
        var target: NormalMember? = null,
        var playing: Boolean = false,
        val data: MutableList<String> = mutableListOf(),
        var numbers: MutableList<Int> = mutableListOf()
    )

    private val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")

    private val command = listOf<suspend (NormalMember) -> String>(
        { "ta的网名中含有${it.nick.random()}" },
        { "ta的入群时间为:${sdf.format(Date(it.joinTimestamp.toLong() * 1000L))}" },
        { "ta的最后发言时间:${sdf.format(Date(it.lastSpeakTimestamp.toLong() * 1000L))}" },
        { "ta的性别:${if (it.queryProfile().sex == UserProfile.Sex.MALE && it.queryProfile().sex != UserProfile.Sex.UNKNOWN) "男" else "女"}" },
        { "ta的QQ等级:${it.queryProfile().qLevel}" },
        { "ta的个性签名:${it.queryProfile().sign}" },
        { "ta的年龄:${it.queryProfile().age}" }
    )

    private val data: MutableMap<Long, Data4Group> = mutableMapOf()

    @SubCommand // 标记这是指令处理器  // 函数名随意
    suspend fun MemberCommandSenderOnMessage.play() {
        val target = data[this.group.id] ?: let {
            data[this.group.id] = Data4Group()
            data[this.group.id]!!
        }

        if (target.playing) {
            sendMessage("游戏进行中")
            return
        }

        target.playing = true
        // 这里可以加个配置文件筛选，比如在配置文件配置只选择最近一个星期发言的群友作为目标
        target.target = this.group.members.random()
        target.numbers = command.indices.toMutableList()

        sendMessage("游戏开始！下面将发送第一条线索。如果回答错误，将随机发送一条线索！")
        val num = target.numbers.random().apply { target.numbers.remove(this) }
        sendMessage(command[num](target.target!!))
    }

    //  猜群友
    @SubCommand
    suspend fun MemberCommandSenderOnMessage.guess(target: User) {
        val dataTarget = data[this.group.id] ?: let {
            data[this.group.id] = Data4Group()
            data[this.group.id]!!
        }
        if (!dataTarget.playing) {
            this.sendMessage("游戏未开始！")
        } else {
            if (target.id == dataTarget.target!!.id) {
                this.sendMessage(At(this.user).plus("恭喜你回答正确！"))
                dataTarget.playing = false
            } else {
                if (dataTarget.numbers.isEmpty()) {
                    this.sendMessage("看来没人能猜到我，我要公布答案了：这位群友是:${dataTarget.target!!.nameCardOrNick}")
                    dataTarget.playing = false
                } else {
                    val num = dataTarget.numbers.random().apply { dataTarget.numbers.remove(this) }
                    this.sendMessage(At(this.user).plus("回答错误！出现新提示：").plus(command[num](dataTarget.target!!)))
                }
            }
        }
    }
}