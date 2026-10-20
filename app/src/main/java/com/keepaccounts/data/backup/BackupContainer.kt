package com.keepaccounts.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件的物理格式。
 *
 * 明文：直接是 zip 字节（以 "PK" 开头）
 * 加密：`KABK` + 版本(1B) + saltLen(1B) + salt + ivLen(1B) + iv + 密文
 *
 * 用口令派生密钥（迭代 12 万次），**不用**数据库那把 Keystore 密钥 —— 后者绑定设备，
 * 用它加密出来的备份换手机就打不开了，等于造了个死文件。
 */
object BackupContainer {

    private val MAGIC = byteArrayOf('K'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Int = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val JSON_ENTRY = "backup.json"

    /** 口令派生算法：PBKDF2 + HMAC-SHA256，迭代 12 万次 */
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"

    class WrongPasswordException : Exception("密码不对，或者这个备份文件已损坏")

    class CorruptFileException(message: String) : Exception(message)

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /** 打包：zip 后按需加密。password 为空则输出明文 zip */
    fun pack(json: String, password: String?): ByteArray {
        val zipped = zip(json)
        return if (password.isNullOrEmpty()) zipped else encrypt(zipped, password)
    }

    /** 解包：先看魔数决定要不要解密，再解 zip */
    fun unpack(bytes: ByteArray, password: String?): String {
        val raw = if (isEncrypted(bytes)) {
            if (password.isNullOrEmpty()) throw WrongPasswordException()
            decrypt(bytes, password)
        } else {
            bytes
        }
        return unzip(raw)
    }

    private fun zip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry(JSON_ENTRY))
            zos.write(text.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == JSON_ENTRY) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        throw CorruptFileException("这个文件里没有账本数据，可能选错了文件")
    }

    private fun encrypt(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)

        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION)
        out.write(salt.size)
        out.write(salt)
        out.write(iv.size)
        out.write(iv)
        out.write(cipherText)
        return out.toByteArray()
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray {
        try {
            var pos = MAGIC.size
            @Suppress("UNUSED_VARIABLE")
            val version = data[pos].toInt()
            pos += 1

            val saltLen = data[pos].toInt() and 0xFF
            pos += 1
            val salt = data.copyOfRange(pos, pos + saltLen)
            pos += saltLen

            val ivLen = data[pos].toInt() and 0xFF
            pos += 1
            val iv = data.copyOfRange(pos, pos + ivLen)
            pos += ivLen

            val cipherText = data.copyOfRange(pos, data.size)

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return cipher.doFinal(cipherText)
        } catch (e: Exception) {
            // GCM 校验失败就是密码错或文件被动过，两种都不该继续
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance(KEY_ALGORITHM)
            .generateSecret(spec)
            .encoded
    }
}
