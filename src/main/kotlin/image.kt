package org.fenglin

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import org.jetbrains.skia.*
import kotlin.math.min
import kotlin.random.Random

fun Image.toByteArray(format: EncodedImageFormat = EncodedImageFormat.PNG) = encodeToData(format)!!.bytes
fun ByteArray.toImage() = Image.makeFromEncoded(this)
suspend fun Contact.uploadImage(image: Image) = uploadImage(image.toByteArray())
suspend fun Contact.uploadImage(bytes: ByteArray) = bytes.toExternalResource().use { it.uploadAsImage(this) }
fun Surface.withCanvas(block: Canvas.() -> Unit) = run {
    canvas.block()
    makeImageSnapshot()
}

/**
 * 裁剪出一块头像
 *
 * @param rate 倍率
 */
fun Image.split(rate: Int = 4): Image {
    val size = min(width, height) / rate
    return Surface.makeRasterN32Premul(size, size).withCanvas {
        drawImageRect(
            image = this@split,
            src = Rect.makeXYWH(
                l = (Random.nextInt(this@split.width - size).toFloat()),
                t = (Random.nextInt(this@split.height - size).toFloat()),
                w = size.toFloat(),
                h = size.toFloat()
            ),
            dst = Rect.makeWH(
                w = size.toFloat(),
                h = size.toFloat()
            ),
        )
    }
}

/**
 * 模糊
 */
fun Image.blur(sigma: Float = 0.1F) =
    Surface.makeRasterN32Premul(width, height).withCanvas {
        drawImage(
            image = this@blur,
            left = 0F,
            top = 0F,
            paint = Paint().apply {
                imageFilter = ImageFilter.makeBlur(width * sigma, height * sigma, FilterTileMode.REPEAT)
            }
        )
    }

/**
 * 缩放
 */
fun Image.scale(rate: Int = 10): Image {
    val r = min(width, height) / rate
    val w = width / r
    val h = height / r
    val image = Surface.makeRasterN32Premul(w, h).withCanvas {
        drawImageRect(
            image = this@scale,
            dst = Rect.makeWH(w.toFloat(), h.toFloat())
        )
    }
    return Surface.makeRaster(imageInfo).withCanvas {
        drawImageRect(
            image = image,
            dst = Rect.makeWH(this@scale.width.toFloat(), this@scale.height.toFloat())
        )
    }
}