package com.beyond.words

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

class MainActivity : AppCompatActivity() {
    private lateinit var words: EditText
    private lateinit var status: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var imageName: TextView
    private lateinit var imageMeta: TextView
    private lateinit var wordCount: TextView
    private lateinit var progress: ProgressBar
    private var pending: BeyondCodec.Decoded? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val bg = Color.rgb(247, 248, 252)
    private val surface = Color.WHITE
    private val primary = Color.rgb(67, 56, 202)
    private val primaryDark = Color.rgb(55, 48, 163)
    private val text = Color.rgb(24, 24, 35)
    private val secondary = Color.rgb(103, 103, 120)
    private val border = Color.rgb(225, 226, 235)
    private val success = Color.rgb(22, 101, 52)
    private val error = Color.rgb(185, 28, 28)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        buildUi()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply {
            fillViewport = true
            clipToPadding = false
            setPadding(dp(18), dp(18), dp(18), dp(104))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "Beyond Words"
            textSize = 31f
            setTextColor(text)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        content.addView(title)

        content.addView(TextView(this).apply {
            text = "Exact image ↔ words  •  100% offline"
            textSize = 16f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, dp(20))
        })

        val imageCard = card()
        imageCard.addView(sectionTitle("IMAGE"))
        imagePreview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(238, 239, 245))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
            alpha = 0.55f
        }
        imageCard.addView(imagePreview, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(12) })
        imageName = TextView(this).apply {
            text = "No image selected"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(text)
            setPadding(0, dp(12), 0, 0)
        }
        imageCard.addView(imageName)
        imageMeta = TextView(this).apply {
            text = "Choose an image to create its exact word representation."
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(4), 0, 0)
        }
        imageCard.addView(imageMeta)
        content.addView(imageCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val wordsCard = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(sectionTitle("BEYOND WORDS DATA"), LinearLayout.LayoutParams(0, -2, 1f))
        wordCount = TextView(this).apply {
            text = "0 words"
            textSize = 12f
            setTextColor(secondary)
            gravity = Gravity.CENTER
        }
        row.addView(wordCount)
        wordsCard.addView(row)

        words = EditText(this).apply {
            hint = "Encoded words appear here…\n\nYou can also paste words here to decode an image."
            textSize = 15f
            setTextColor(text)
            setHintTextColor(Color.rgb(155, 156, 170))
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setSingleLine(false)
            minLines = 9
            maxLines = 18
            isVerticalScrollBarEnabled = true
            background = rounded(Color.rgb(250, 250, 253), border, 12)
        }
        wordsCard.addView(words, LinearLayout.LayoutParams(-1, dp(230)).apply { topMargin = dp(10) })

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val copy = smallButton("Copy words")
        copy.setOnClickListener { copyWords() }
        utilityRow.addView(copy, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        val clear = smallButton("Clear")
        clear.setOnClickListener { clearAll() }
        utilityRow.addView(clear, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        wordsCard.addView(utilityRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(wordsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val info = card()
        info.addView(sectionTitle("VERIFICATION"))
        status = TextView(this).apply {
            text = "Ready. Select an image or paste Beyond Words data."
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(10), 0, 0)
        }
        info.addView(status)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        info.addView(progress, LinearLayout.LayoutParams(-2, dp(28)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        })
        content.addView(info)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(surface, border, 0)
            elevation = dp(10).toFloat()
        }
        val encode = primaryButton("ENCODE IMAGE")
        encode.setOnClickListener { pick() }
        bottom.addView(encode, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(5) })
        val decode = primaryButton("DECODE → IMAGE")
        decode.setOnClickListener { decode() }
        bottom.addView(decode, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        val bottomParams = FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM)
        root.addView(bottom, bottomParams)

        setContentView(root)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(surface, border, 18)
        elevation = dp(2).toFloat()
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        letterSpacing = 0.12f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(secondary)
    }

    private fun primaryButton(label: String) = Button(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = rounded(primary, primaryDark, 16)
        stateListAnimator = null
        elevation = dp(2).toFloat()
        minHeight = 0
        minimumHeight = 0
    }

    private fun smallButton(label: String) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(primary)
        isAllCaps = false
        background = rounded(Color.rgb(243, 242, 255), Color.rgb(220, 218, 250), 12)
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        if (radius > 0) cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, 10)
    }

    private fun decode() {
        val input = words.text.toString().trim()
        if (input.isBlank()) {
            setStatus("Paste or generate Beyond Words data first.", false)
            return
        }
        busy(true)
        executor.execute {
            try {
                val d = BeyondCodec.decode(input)
                pending = d
                runOnUiThread {
                    busy(false)
                    setStatus("✓ Integrity verified • exact ${d.bytes.size} bytes recovered • SHA-256 OK", true)
                    val title = d.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "recovered_image" }
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = d.mime
                        putExtra(Intent.EXTRA_TITLE, title)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, 20)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy(false)
                    setStatus("✕ Decode failed: ${e.message ?: "invalid data"}", false)
                }
            }
        }
    }

    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req, result, data)
        if (result != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (req == 10) {
            busy(true)
            executor.execute {
                try {
                    val raw = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val name = queryName(uri) ?: "image"
                    val mime = contentResolver.getType(uri) ?: "image/*"
                    val encoded = BeyondCodec.encode(raw, name, mime)
                    runOnUiThread {
                        words.setText(encoded)
                        updateWordCount(encoded)
                        imagePreview.setImageURI(uri)
                        imagePreview.alpha = 1f
                        imageName.text = name
                        imageMeta.text = "${formatBytes(raw.size.toLong())} • ${mime}"
                        setStatus("✓ Encoded exactly • SHA-256 stored in the data header", true)
                        busy(false)
                        words.requestFocus()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        busy(false)
                        setStatus("✕ Encode failed: ${e.message ?: "unknown error"}", false)
                    }
                }
            }
        } else if (req == 20) {
            val d = pending ?: return
            executor.execute {
                try {
                    contentResolver.openOutputStream(uri)!!.use { it.write(d.bytes) }
                    runOnUiThread {
                        setStatus("✓ Image saved • ${formatBytes(d.bytes.size.toLong())} • byte-for-byte verified", true)
                        pending = null
                    }
                } catch (e: Exception) {
                    runOnUiThread { setStatus("✕ Save failed: ${e.message ?: "unknown error"}", false) }
                }
            }
        }
    }

    private fun updateWordCount(encoded: String) {
        val count = encoded.trim().split(Regex("\\s+")).size.let { if (encoded.startsWith("BW2 ")) it - 1 else it }
        wordCount.text = "$count words"
    }

    private fun copyWords() {
        val value = words.text.toString().trim()
        if (value.isBlank()) {
            setStatus("Nothing to copy yet.", false)
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Beyond Words", value))
        setStatus("✓ Words copied to clipboard", true)
    }

    private fun clearAll() {
        words.text.clear()
        imagePreview.setImageResource(android.R.drawable.ic_menu_gallery)
        imagePreview.alpha = 0.55f
        imageName.text = "No image selected"
        imageMeta.text = "Choose an image to create its exact word representation."
        wordCount.text = "0 words"
        pending = null
        setStatus("Ready. Select an image or paste Beyond Words data.", true)
    }

    private fun busy(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun setStatus(message: String, ok: Boolean) {
        status.text = message
        status.setTextColor(if (ok) success else error)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun queryName(uri: Uri): String? = contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}

private object BeyondCodec {
    private const val MAGIC = "BW2"
    private const val BITS = 12
    private const val N = 4096
    private val syllables = arrayOf(
        "ba", "be", "bi", "bo", "bu", "ca", "ce", "ci", "co", "cu",
        "da", "de", "di", "do", "du", "fa", "fe", "fi", "fo", "fu",
        "ga", "ge", "gi", "go", "gu", "ha", "he", "hi", "ho", "hu",
        "ja", "je", "ji", "jo", "ju", "ka", "ke", "ki", "ko", "ku",
        "la", "le", "li", "lo", "lu", "ma", "me", "mi", "mo", "mu",
        "na", "ne", "ni", "no", "nu", "pa", "pe", "pi", "po", "pu",
        "ra", "re", "ri", "ro", "ru", "sa", "se", "si", "so", "su"
    )

    private fun token(v: Int) = syllables[v / 64] + syllables[v % 64]

    private fun value(w: String): Int {
        if (w.length != 4) throw IllegalArgumentException("Invalid Beyond word: $w")
        val a = syllables.indexOf(w.substring(0, 2))
        val b = syllables.indexOf(w.substring(2))
        if (a < 0 || b < 0) throw IllegalArgumentException("Invalid Beyond word: $w")
        return a * 64 + b
    }

    data class Decoded(val bytes: ByteArray, val name: String, val mime: String)

    fun encode(raw: ByteArray, name: String, mime: String): String {
        val c = deflate(raw)
        val compressed = c.size < raw.size
        val payload = if (compressed) c else raw
        val nb = name.toByteArray(Charsets.UTF_8)
        val mb = mime.toByteArray(Charsets.UTF_8)
        if (nb.size > 65535 || mb.size > 65535) throw IllegalArgumentException("Metadata too long")
        val h = ByteArrayOutputStream()
        h.write(MAGIC.toByteArray())
        h.write(2)
        h.write(if (compressed) 1 else 0)
        put16(h, nb.size)
        put16(h, mb.size)
        put64(h, raw.size.toLong())
        put64(h, payload.size.toLong())
        h.write(MessageDigest.getInstance("SHA-256").digest(raw))
        h.write(nb)
        h.write(mb)
        val body = h.toByteArray() + payload
        val crc = CRC32().apply { update(body) }.value
        val all = body + byteArrayOf(
            (crc ushr 24).toByte(), (crc ushr 16).toByte(),
            (crc ushr 8).toByte(), crc.toByte()
        )
        val out = StringBuilder(MAGIC)
        var buf = 0
        var bits = 0
        for (x in all) {
            buf = (buf shl 8) or (x.toInt() and 255)
            bits += 8
            while (bits >= BITS) {
                bits -= BITS
                out.append(' ').append(token((buf ushr bits) and (N - 1)))
            }
        }
        if (bits > 0) out.append(' ').append(token((buf shl (BITS - bits)) and (N - 1)))
        return out.toString()
    }

    fun decode(text: String): Decoded {
        val t = text.trim().split(Regex("\\s+"))
        if (t.firstOrNull() != MAGIC) throw IllegalArgumentException("Missing BW2 header")
        if (t.size < 3) throw IllegalArgumentException("Word sequence is too short")
        val vals = t.drop(1).map(::value)
        val bytes = ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (v in vals) {
            buf = (buf shl BITS) or v
            bits += BITS
            while (bits >= 8) {
                bits -= 8
                bytes.write((buf ushr bits) and 255)
            }
        }
        val allWithPadding = bytes.toByteArray()
        val fixed = 3 + 1 + 1 + 2 + 2 + 8 + 8 + 32
        if (allWithPadding.size < fixed + 4) throw IllegalArgumentException("Word sequence is too short")
        var p = 0
        fun u8() = allWithPadding[p++].toInt() and 255
        fun u16() = (u8() shl 8) or u8()
        fun u64(): Long {
            var x = 0L
            repeat(8) { x = (x shl 8) or u8().toLong() }
            return x
        }
        if (String(allWithPadding.copyOfRange(0, 3), Charsets.US_ASCII) != MAGIC) throw IllegalArgumentException("Invalid container")
        p = 3
        val ver = u8()
        if (ver != 2) throw IllegalArgumentException("Unsupported version")
        val codec = u8()
        val nl = u16()
        val ml = u16()
        val rawLen = u64()
        val payLen = u64()
        val sha = allWithPadding.copyOfRange(p, p + 32)
        p += 32
        if (payLen < 0 || payLen > Int.MAX_VALUE) throw IllegalArgumentException("Image is too large")
        val expected = p.toLong() + nl + ml + payLen + 4L
        if (expected > allWithPadding.size) throw IllegalArgumentException("Truncated word sequence")
        if (expected < 0 || expected > Int.MAX_VALUE) throw IllegalArgumentException("Container is too large")
        val all = allWithPadding.copyOf(expected.toInt())
        if (allWithPadding.size > all.size && allWithPadding.copyOfRange(all.size, allWithPadding.size).any { it.toInt() != 0 }) {
            throw IllegalArgumentException("Invalid padding")
        }
        p = 3 + 1 + 1 + 2 + 2 + 8 + 8 + 32
        val name = String(all.copyOfRange(p, p + nl), Charsets.UTF_8)
        p += nl
        val mime = String(all.copyOfRange(p, p + ml), Charsets.UTF_8)
        p += ml
        val payload = all.copyOfRange(p, p + payLen.toInt())
        p += payLen.toInt()
        val got = ((all[p].toLong() and 255) shl 24) or
                ((all[p + 1].toLong() and 255) shl 16) or
                ((all[p + 2].toLong() and 255) shl 8) or
                (all[p + 3].toLong() and 255)
        val crc = CRC32().apply { update(all, 0, p) }.value
        if (got != crc) throw IllegalArgumentException("CRC failed — word sequence is corrupted")
        val raw = if (codec == 1) inflate(payload) else payload
        if (raw.size.toLong() != rawLen) throw IllegalArgumentException("Length verification failed")
        if (!MessageDigest.getInstance("SHA-256").digest(raw).contentEquals(sha)) {
            throw IllegalArgumentException("SHA-256 failed — not the exact original")
        }
        return Decoded(raw, name, mime)
    }

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(9)
        d.setInput(b)
        d.finish()
        val o = ByteArrayOutputStream()
        val z = ByteArray(8192)
        while (!d.finished()) o.write(z, 0, d.deflate(z))
        d.end()
        return o.toByteArray()
    }

    private fun inflate(b: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(b)
        val o = ByteArrayOutputStream()
        val z = ByteArray(8192)
        try {
            while (!i.finished()) {
                val n = i.inflate(z)
                if (n > 0) o.write(z, 0, n)
                if (n == 0 && i.needsInput()) throw IllegalArgumentException("Missing compressed data")
                if (n == 0 && i.needsDictionary()) throw IllegalArgumentException("Compressed data needs a dictionary")
            }
        } catch (e: Exception) {
            if (e is IllegalArgumentException && (e.message?.startsWith("Missing") == true || e.message?.startsWith("Compressed") == true)) throw e
            throw IllegalArgumentException("Invalid compressed data")
        } finally {
            i.end()
        }
        return o.toByteArray()
    }

    private fun put16(o: ByteArrayOutputStream, v: Int) {
        o.write(v ushr 8)
        o.write(v)
    }

    private fun put64(o: ByteArrayOutputStream, v: Long) {
        for (s in 7 downTo 0) o.write((v ushr (s * 8)).toInt())
    }
}
