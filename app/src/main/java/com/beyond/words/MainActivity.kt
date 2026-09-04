package com.beyond.words

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

class MainActivity : AppCompatActivity() {
    private lateinit var words: EditText
    private lateinit var status: TextView
    private var pending: BeyondCodec.Decoded? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,28,28,20) }
        root.addView(TextView(this).apply { text="Beyond Words"; textSize=30f; setPadding(0,0,0,4) })
        root.addView(TextView(this).apply { text="Exact Image ↔ Words • Offline"; textSize=16f; setPadding(0,0,0,20) })
        root.addView(Button(this).apply { text="🖼  Encode Image → Words"; setOnClickListener { pick() } }, LinearLayout.LayoutParams(-1,64).apply { bottomMargin=12 })
        words = EditText(this).apply { hint="Paste Beyond words here…"; gravity=Gravity.TOP; minLines=10; setTextIsSelectable(true) }
        root.addView(words, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { text="🔤  Decode Words → Image"; setOnClickListener { decode() } }, LinearLayout.LayoutParams(-1,64).apply { topMargin=12 })
        status = TextView(this).apply { text="Ready • Core operation works offline"; textSize=14f; setPadding(0,14,0,0) }
        root.addView(status)
        setContentView(root)
    }

    private fun pick() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 10) }

    private fun decode() {
        try {
            pending = BeyondCodec.decode(words.text.toString())
            val d = pending!!
            val title = d.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "recovered_image" }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type=d.mime; putExtra(Intent.EXTRA_TITLE,title); addCategory(Intent.CATEGORY_OPENABLE) }, 20)
        } catch (e: Exception) { status.text="Decode failed: ${e.message}" }
    }

    override fun onActivityResult(req:Int, result:Int, data:Intent?) {
        super.onActivityResult(req,result,data)
        if (result != RESULT_OK || data?.data == null) return
        val uri=data.data!!
        try {
            if (req==10) {
                val raw=contentResolver.openInputStream(uri)!!.use{it.readBytes()}
                val name=queryName(uri) ?: "image"
                val mime=contentResolver.getType(uri) ?: "image/*"
                words.setText(BeyondCodec.encode(raw,name,mime))
                status.text="Encoded ${raw.size} bytes • ${words.text.trim().split(Regex("\\s+")).size-1} data words"
            } else if (req==20) {
                val d=pending ?: return
                contentResolver.openOutputStream(uri)!!.use { it.write(d.bytes) }
                status.text="✓ Exact image recovered • SHA-256 verified"
                pending=null
            }
        } catch(e:Exception) { status.text="Operation failed: ${e.message}" }
    }

    private fun queryName(uri:Uri):String? = contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}
}

private object BeyondCodec {
    private const val MAGIC="BW2"; private const val BITS=12; private const val N=4096
    private val syllables = arrayOf("ba","be","bi","bo","bu","ca","ce","ci","co","cu","da","de","di","do","du","fa","fe","fi","fo","fu","ga","ge","gi","go","gu","ha","he","hi","ho","hu","ja","je","ji","jo","ju","ka","ke","ki","ko","ku","la","le","li","lo","lu","ma","me","mi","mo","mu","na","ne","ni","no","nu","pa","pe","pi","po","pu","ra","re","ri","ro","ru","sa","se","si","so","su")
    private fun token(v:Int)=syllables[v/64]+syllables[v%64]
    private fun value(w:String):Int { if(w.length!=4) throw IllegalArgumentException("Invalid Beyond word: $w"); val a=syllables.indexOf(w.substring(0,2)); val b=syllables.indexOf(w.substring(2)); if(a<0||b<0)throw IllegalArgumentException("Invalid Beyond word: $w"); return a*64+b }
    data class Decoded(val bytes:ByteArray,val name:String,val mime:String)

    fun encode(raw:ByteArray,name:String,mime:String):String {
        val c=deflate(raw); val compressed=c.size<raw.size; val payload=if(compressed)c else raw
        val nb=name.toByteArray(Charsets.UTF_8); val mb=mime.toByteArray(Charsets.UTF_8)
        if(nb.size>65535||mb.size>65535)throw IllegalArgumentException("Metadata too long")
        val h=ByteArrayOutputStream(); h.write(MAGIC.toByteArray()); h.write(2); h.write(if(compressed)1 else 0); put16(h,nb.size); put16(h,mb.size); put64(h,raw.size.toLong()); put64(h,payload.size.toLong()); h.write(MessageDigest.getInstance("SHA-256").digest(raw)); h.write(nb); h.write(mb)
        val body=h.toByteArray()+payload; val crc=CRC32().apply{update(body)}.value; val all=body+byteArrayOf((crc ushr 24).toByte(),(crc ushr 16).toByte(),(crc ushr 8).toByte(),crc.toByte())
        val out=StringBuilder(MAGIC); var buf=0; var bits=0
        for(x in all){buf=(buf shl 8) or (x.toInt() and 255); bits+=8; while(bits>=BITS){bits-=BITS; out.append(' ').append(token((buf ushr bits) and (N-1)))}}
        if(bits>0)out.append(' ').append(token((buf shl (BITS-bits)) and (N-1)))
        return out.toString()
    }

    fun decode(text:String):Decoded {
        val t=text.trim().split(Regex("\\s+")); if(t.firstOrNull()!=MAGIC)throw IllegalArgumentException("Missing BW2 header")
        val vals=t.drop(1).map(::value); val bytes=ByteArrayOutputStream(); var buf=0;var bits=0
        for(v in vals){buf=(buf shl BITS) or v;bits+=BITS;while(bits>=8){bits-=8;bytes.write((buf ushr bits) and 255)}}
        val all=bytes.toByteArray(); val fixed=3+1+1+2+2+8+8+32; if(all.size<fixed+4)throw IllegalArgumentException("Word sequence is too short")
        var p=0; fun u8()=all[p++].toInt() and 255; fun u16()=(u8() shl 8) or u8(); fun u64():Long{var x=0L;repeat(8){x=(x shl 8) or u8().toLong()};return x}
        if(String(all.copyOfRange(0,3),Charsets.US_ASCII)!=MAGIC)throw IllegalArgumentException("Invalid container");p=3;val ver=u8();if(ver!=2)throw IllegalArgumentException("Unsupported version");val codec=u8();val nl=u16();val ml=u16();val rawLen=u64();val payLen=u64();val sha=all.copyOfRange(p,p+32);p+=32
        if(payLen<0||payLen>Int.MAX_VALUE)throw IllegalArgumentException("Image is too large"); if(p+nl+ml+payLen.toInt()+4>all.size)throw IllegalArgumentException("Truncated word sequence")
        val name=String(all.copyOfRange(p,p+nl),Charsets.UTF_8);p+=nl;val mime=String(all.copyOfRange(p,p+ml),Charsets.UTF_8);p+=ml;val payload=all.copyOfRange(p,p+payLen.toInt());p+=payLen.toInt()
        val got=((all[p].toLong() and 255) shl 24) or ((all[p+1].toLong() and 255) shl 16) or ((all[p+2].toLong() and 255) shl 8) or (all[p+3].toLong() and 255);val crc=CRC32().apply{update(all,0,p)}.value;if(got!=crc)throw IllegalArgumentException("CRC failed — word sequence is corrupted")
        val raw=if(codec==1)inflate(payload)else payload;if(raw.size.toLong()!=rawLen)throw IllegalArgumentException("Length verification failed");if(!MessageDigest.getInstance("SHA-256").digest(raw).contentEquals(sha))throw IllegalArgumentException("SHA-256 failed — not the exact original")
        return Decoded(raw,name,mime)
    }
    private fun deflate(b:ByteArray):ByteArray{val d=Deflater(9);d.setInput(b);d.finish();val o=ByteArrayOutputStream();val z=ByteArray(8192);while(!d.finished())o.write(z,0,d.deflate(z));d.end();return o.toByteArray()}
    private fun inflate(b:ByteArray):ByteArray{val i=Inflater();i.setInput(b);val o=ByteArrayOutputStream();val z=ByteArray(8192);try{while(!i.finished()){val n=i.inflate(z);if(n==0&&i.needsInput())throw IllegalArgumentException();o.write(z,0,n)}}catch(e:Exception){throw IllegalArgumentException("Invalid compressed data")};finally{i.end()};return o.toByteArray()}
    private fun put16(o:ByteArrayOutputStream,v:Int){o.write(v ushr 8);o.write(v)}
    private fun put64(o:ByteArrayOutputStream,v:Long){for(s in 7 downTo 0)o.write((v ushr(s*8)).toInt())}
}
