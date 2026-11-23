package uts.sdk.modules.hgEdge

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * dx150-edge-v1 加密原语 + 图片 AES-CBC
 */
object DxCrypto {
    private val secureRandom = SecureRandom()

    /** 每 scope 发号 / 当前放行号：跨协程原子递增，杜绝 Promise 链竞态导致的 seq replay */
    private val scopeTicket = ConcurrentHashMap<String, AtomicInteger>()
    private val scopeServing = ConcurrentHashMap<String, AtomicInteger>()

    fun nextScopeTicket(scope: String): Int {
        return scopeTicket.computeIfAbsent(scope) { AtomicInteger(0) }.incrementAndGet()
    }

    fun scopeServing(scope: String): Int {
        return scopeServing.computeIfAbsent(scope) { AtomicInteger(0) }.get()
    }

    fun advanceScopeServing(scope: String, ticket: Int) {
        scopeServing.computeIfAbsent(scope) { AtomicInteger(0) }.set(ticket)
    }

    private const val B64_URL = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private const val B64_STD = Base64.NO_WRAP

    private val IMAGE_KEY = "f5d965df75336270".toByteArray(Charsets.UTF_8)
    private val IMAGE_IV = "97b60394abc2fbe1".toByteArray(Charsets.UTF_8)

    fun randomHex(byteLenStr: String): String {
        val byteLen = byteLenStr.toInt()
        val bytes = ByteArray(byteLen)
        secureRandom.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    /**
     * 返回 JSON: {"priv":"<std b64 d>","pub":"<b64url 0x04||X||Y>"}
     */
    fun generateClientKeyPair(): String {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()
        val priv = kp.private as ECPrivateKey
        val pub = kp.public as ECPublicKey
        val dBytes = bigIntToFixed(priv.s, 32)
        val pubBytes = encodeUncompressed(pub.w)
        val obj = org.json.JSONObject()
        obj.put("priv", Base64.encodeToString(dBytes, B64_STD))
        obj.put("pub", Base64.encodeToString(pubBytes, B64_URL))
        return obj.toString()
    }

    /**
     * ECDH + 双路 HKDF
     * 返回 JSON: {"req":"<std b64>","res":"<std b64>","info":"<utf8 info base>"}
     * infoBase = "dx150-edge-v1\\nscope\\nsite\\nkid\\nsid"（不含 \\nreq/\\nres）
     */
    fun deriveSessionKeys(
        privB64: String,
        serverPubB64url: String,
        saltB64url: String,
        scope: String,
        site: String,
        kid: String,
        sid: String
    ): String {
        val d = Base64.decode(privB64, B64_STD)
        val serverPub = Base64.decode(serverPubB64url, B64_URL)
        val salt = Base64.decode(saltB64url, B64_URL)
        val shared = ecdhSharedX(d, serverPub)
        val infoBase = "dx150-edge-v1\n$scope\n$site\n$kid\n$sid"
        val reqKey = hkdfSha256(shared, salt, (infoBase + "\nreq").toByteArray(Charsets.UTF_8), 32)
        val resKey = hkdfSha256(shared, salt, (infoBase + "\nres").toByteArray(Charsets.UTF_8), 32)
        val obj = org.json.JSONObject()
        obj.put("req", Base64.encodeToString(reqKey, B64_STD))
        obj.put("res", Base64.encodeToString(resKey, B64_STD))
        obj.put("info", infoBase)
        return obj.toString()
    }

    fun aesGcmEncrypt(keyB64: String, seqStr: String, aadUtf8: String, plaintextUtf8: String): String {
        val key = Base64.decode(keyB64, B64_STD)
        val iv = buildIv(seqStr.toInt())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aadUtf8.toByteArray(Charsets.UTF_8))
        val out = cipher.doFinal(plaintextUtf8.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, B64_URL)
    }

    fun aesGcmDecrypt(keyB64: String, seqStr: String, aadUtf8: String, ciphertextB64url: String): String {
        val key = Base64.decode(keyB64, B64_STD)
        val iv = buildIv(seqStr.toInt())
        val ct = Base64.decode(ciphertextB64url, B64_URL)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aadUtf8.toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(ct)
        return String(plain, Charsets.UTF_8)
    }

    fun decryptImageAesCbc(cipherStdB64: String): String {
        val ct = Base64.decode(cipherStdB64, B64_STD)
        val plain = decryptImageBytesSmart(ct)
        return Base64.encodeToString(plain, B64_STD)
    }

    /**
     * 下载加密封面并解密到缓存文件，返回本地绝对路径
     */
    fun fetchDecryptImageToFile(url: String, cacheDir: String): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Referer", "https://huangguoai.com/")
        conn.setRequestProperty("User-Agent", "HuangguoAndroid/1.0")
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("image http $code")
            }
            val raw = conn.inputStream.use { it.readBytes() }
            val plain = decryptImageBytesSmart(raw)
            val dir = java.io.File(cacheDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = "hg_" + Integer.toHexString(url.hashCode()) + "_" + System.currentTimeMillis() + imageExt(plain)
            val file = java.io.File(dir, name)
            file.writeBytes(plain)
            return file.absolutePath
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 智能解密：明文图直接透传；密文优先 PKCS7，失败再 NoPadding（与站点前端一致）。
     * 避免对已是 JPEG 的字节再解一次导致绿屏花屏。
     */
    private fun decryptImageBytesSmart(raw: ByteArray): ByteArray {
        if (isImageMagic(raw)) {
            return raw
        }
        if (raw.size < 16) {
            throw IllegalStateException("image too short")
        }
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(IMAGE_KEY, "AES"), IvParameterSpec(IMAGE_IV))
            val plain = cipher.doFinal(raw)
            if (isImageMagic(plain)) {
                return plain
            }
        } catch (_: Exception) {
            // 部分 CDN 密文填充不标准，改走 NoPadding
        }
        val aligned = raw.size - (raw.size % 16)
        if (aligned < 16) {
            throw IllegalStateException("image block invalid")
        }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(IMAGE_KEY, "AES"), IvParameterSpec(IMAGE_IV))
        val plain = cipher.doFinal(if (aligned == raw.size) raw else raw.copyOfRange(0, aligned))
        if (isImageMagic(plain)) {
            return plain
        }
        val trimmed = stripTrailingPadding(plain)
        if (isImageMagic(trimmed)) {
            return trimmed
        }
        throw IllegalStateException("decrypt not image")
    }

    private fun isImageMagic(b: ByteArray): Boolean {
        if (b.size < 3) {
            return false
        }
        // JPEG
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) {
            return true
        }
        if (b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) {
            return true
        }
        if (b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte()) {
            return true
        }
        if (b.size >= 12 &&
            b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x46.toByte() &&
            b[8] == 0x57.toByte() && b[9] == 0x45.toByte() && b[10] == 0x42.toByte() && b[11] == 0x50.toByte()
        ) {
            return true
        }
        return false
    }

    private fun stripTrailingPadding(b: ByteArray): ByteArray {
        if (b.isEmpty()) {
            return b
        }
        val pad = b[b.size - 1].toInt() and 0xff
        if (pad < 1 || pad > 16 || pad > b.size) {
            return b
        }
        for (i in 0 until pad) {
            if ((b[b.size - 1 - i].toInt() and 0xff) != pad) {
                return b
            }
        }
        return b.copyOfRange(0, b.size - pad)
    }

    private fun imageExt(b: ByteArray): String {
        if (b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) {
            return ".jpg"
        }
        if (b.size >= 4 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte()) {
            return ".png"
        }
        if (b.size >= 12 && b[8] == 0x57.toByte() && b[9] == 0x45.toByte()) {
            return ".webp"
        }
        return ".jpg"
    }

    fun stdB64EncodeUtf8(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), B64_STD)
    }

    fun stdB64DecodeToUtf8(b64: String): String {
        val bytes = Base64.decode(b64, B64_STD)
        return String(bytes, Charsets.UTF_8)
    }

    private fun buildIv(seq: Int): ByteArray {
        val iv = ByteArray(12)
        iv[8] = ((seq ushr 24) and 0xff).toByte()
        iv[9] = ((seq ushr 16) and 0xff).toByte()
        iv[10] = ((seq ushr 8) and 0xff).toByte()
        iv[11] = (seq and 0xff).toByte()
        return iv
    }

    private fun ecdhSharedX(privD: ByteArray, serverUncompressed: ByteArray): ByteArray {
        if (serverUncompressed.size != 65 || serverUncompressed[0] != 0x04.toByte()) {
            throw IllegalArgumentException("invalid server_pub")
        }
        val params = (KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair().public as ECPublicKey).params

        val x = BigInteger(1, serverUncompressed.copyOfRange(1, 33))
        val y = BigInteger(1, serverUncompressed.copyOfRange(33, 65))
        val kf = KeyFactory.getInstance("EC")
        val pub = kf.generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
        val priv = kf.generatePrivate(ECPrivateKeySpec(BigInteger(1, privD), params)) as ECPrivateKey

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        val secret = ka.generateSecret()
        // Android 可能返回未定长，规范取 32 字节 X
        return if (secret.size == 32) {
            secret
        } else if (secret.size > 32) {
            secret.copyOfRange(secret.size - 32, secret.size)
        } else {
            val padded = ByteArray(32)
            System.arraycopy(secret, 0, padded, 32 - secret.size, secret.size)
            padded
        }
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(0x01.toByte())
        val okm = mac.doFinal()
        return okm.copyOf(length)
    }

    private fun encodeUncompressed(point: ECPoint): ByteArray {
        val x = bigIntToFixed(point.affineX, 32)
        val y = bigIntToFixed(point.affineY, 32)
        val out = ByteArray(65)
        out[0] = 0x04
        System.arraycopy(x, 0, out, 1, 32)
        System.arraycopy(y, 0, out, 33, 32)
        return out
    }

    private fun bigIntToFixed(v: BigInteger, size: Int): ByteArray {
        var bytes = v.toByteArray()
        if (bytes.size == size) return bytes
        if (bytes.size == size + 1 && bytes[0] == 0.toByte()) {
            return bytes.copyOfRange(1, bytes.size)
        }
        if (bytes.size > size) {
            return bytes.copyOfRange(bytes.size - size, bytes.size)
        }
        val out = ByteArray(size)
        System.arraycopy(bytes, 0, out, size - bytes.size, bytes.size)
        return out
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
