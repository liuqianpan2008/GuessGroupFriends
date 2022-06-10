package org.fenglin

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.contact.NormalMember
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.data.UserProfile
import net.mamoe.mirai.message.data.At
import org.fenglin.Command.data
import org.fenglin.Command.normalMember
import org.fenglin.Command.playing
import java.text.SimpleDateFormat
import java.util.*


object Command : CompositeCommand(
    GuessGroupFriends,
    "猜群友",
    "猜群友游戏",
    description = "开始猜群友！"
) {
    var normalMember: NormalMember? = null
    var playing:Boolean=false;
    var data:ArrayList<String>?=null
    @SubCommand // 标记这是指令处理器  // 函数名随意
    suspend fun CommandSender.paly() {
        if (playing){
            sendMessage("游戏进行中")
            return;
        }
        this.subject?.id?.let { it ->
//          获取群友
            val members =  this.bot?.getGroup(it)?.members?.toList()
            normalMember = members?.get((0 until members.size).random())
            if (normalMember==null){
                sendMessage("获取群友失败")
                return;
            }
            //获取信息
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            data= arrayListOf(
                "ta的网名中含有${normalMember!!.nick.random()}",
                "ta的入群时间为:${sdf.format(Date(normalMember!!.joinTimestamp.toLong()*1000L))}",
                "ta的最后发言时间:${sdf.format(Date(normalMember!!.lastSpeakTimestamp.toLong()*1000L))}",
                "ta的性别:${if(normalMember?.queryProfile()?.sex== UserProfile.Sex.MALE && normalMember?.queryProfile()?.sex != UserProfile.Sex.UNKNOWN) "男" else "女"}",
                "ta的QQ等级:${normalMember?.queryProfile()?.qLevel?:"没有获取到"}",
                "ta的个性签名:${normalMember?.queryProfile()?.sign?:"没有获取到"}",
                "ta的年龄:${normalMember?.queryProfile()?.age?:"没有获取到"}"
            )
            sendMessage("游戏开始！下面将发送第一条线索。如果回答错误，将随机发送一条线索！")
            sendMessage(random())
            playing = true
        }
    }
//  猜群友
    @SubCommand
    suspend fun CommandSender.guess(target: User) {
        if (!playing) {this.sendMessage("游戏未开始！");} else {
            if (target.id == normalMember?.id) {
                this.sendMessage(At(this.user!!).plus("恭喜你回答正确！"))
            } else {
                this.sendMessage(At(this.user!!).plus("回答错误！出现新提示：").plus(random()))
            }
        }
    }
}
    private fun random():String{
        if (data.isNullOrEmpty()) {
            playing = false;
            return "看来没人能猜到我，我要公布答案了：这位群友是:${normalMember?.nick}"
        } else{
            val r = (0 until data?.size!!).random()
            if (data!!.elementAtOrNull(r)==null){
                playing = false;
                return "看来没人能猜到我，我要公布答案了：这位群友是:${normalMember?.nick}"
            }
            val res = data!![r]
            data?.removeAt(r)
            return res
        }
    }