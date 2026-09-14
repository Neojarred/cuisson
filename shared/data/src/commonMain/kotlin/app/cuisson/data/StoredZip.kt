package app.cuisson.data

class ExportException(message: String) : Exception(message)

/**
 * A zip file with nothing compressed.
 *
 * Deliberately the plainest zip there is. Any unzip tool on any computer opens it, which is
 * the point of an Export File format that promises the data belongs to the user, and it needs no
 * library, so the same code serves the iOS app later. Compression would buy little: the
 * images are already JPEG or WebP, and the text of a few hundred recipes is small.
 */
internal object StoredZip {

    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteWriter()
        val central = ByteWriter()
        entries.forEach { (name, data) ->
            val nameBytes = name.encodeToByteArray()
            val crc = Crc32.of(data)
            val offset = out.size

            out.u32(LOCAL)
            out.u16(20); out.u16(UTF8_NAMES); out.u16(0); out.u16(0); out.u16(DOS_DATE)
            out.u32(crc); out.u32(data.size); out.u32(data.size)
            out.u16(nameBytes.size); out.u16(0)
            out.bytes(nameBytes)
            out.bytes(data)

            central.u32(CENTRAL)
            central.u16(20); central.u16(20); central.u16(UTF8_NAMES); central.u16(0)
            central.u16(0); central.u16(DOS_DATE)
            central.u32(crc); central.u32(data.size); central.u32(data.size)
            central.u16(nameBytes.size); central.u16(0); central.u16(0)
            central.u16(0); central.u16(0); central.u32(0); central.u32(offset)
            central.bytes(nameBytes)
        }
        val directory = central.toByteArray()
        val directoryOffset = out.size
        out.bytes(directory)
        out.u32(END)
        out.u16(0); out.u16(0); out.u16(entries.size); out.u16(entries.size)
        out.u32(directory.size); out.u32(directoryOffset); out.u16(0)
        return out.toByteArray()
    }

    /** Every entry by name. Refuses anything damaged rather than returning part of it. */
    fun read(zip: ByteArray): Map<String, ByteArray> = try {
        readUnchecked(zip)
    } catch (e: ExportException) {
        throw e
    } catch (e: Exception) {
        throw ExportException(NOT_AN_EXPORT)
    }

    private fun readUnchecked(zip: ByteArray): Map<String, ByteArray> {
        val lowest = maxOf(0, zip.size - 22 - 0xffff)
        val end = (zip.size - 22 downTo lowest).firstOrNull { u32(zip, it) == END }
            ?: throw ExportException(NOT_AN_EXPORT)
        val count = u16(zip, end + 10)
        var at = u32(zip, end + 16)
        val result = linkedMapOf<String, ByteArray>()
        repeat(count) {
            if (u32(zip, at) != CENTRAL) throw ExportException(DAMAGED)
            val method = u16(zip, at + 10)
            val crc = u32(zip, at + 16)
            val size = u32(zip, at + 20)
            val nameLength = u16(zip, at + 28)
            val extraLength = u16(zip, at + 30)
            val commentLength = u16(zip, at + 32)
            val local = u32(zip, at + 42)
            val name = zip.decodeToString(at + 46, at + 46 + nameLength)
            if (method != 0) throw ExportException(NOT_AN_EXPORT)
            if (u32(zip, local) != LOCAL) throw ExportException(DAMAGED)
            val start = local + 30 + u16(zip, local + 26) + u16(zip, local + 28)
            val data = zip.copyOfRange(start, start + size)
            if (Crc32.of(data) != crc) throw ExportException(DAMAGED)
            result[name] = data
            at += 46 + nameLength + extraLength + commentLength
        }
        return result
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, at: Int): Int = u16(b, at) or (u16(b, at + 2) shl 16)

    private const val LOCAL = 0x04034b50
    private const val CENTRAL = 0x02014b50
    private const val END = 0x06054b50
    private const val UTF8_NAMES = 0x0800
    /** 1 January 1980. The moment an Export File was made is in its manifest, not in here. */
    private const val DOS_DATE = (1 shl 5) or 1

    const val NOT_AN_EXPORT = "This file is not a Cuisson export file."
    const val DAMAGED = "This export file is damaged, so nothing was restored from it."
}

private class ByteWriter {
    private var buffer = ByteArray(1 shl 16)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    fun u16(value: Int) {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
    }

    fun u32(value: Int) {
        ensure(4)
        repeat(4) { buffer[size++] = (value ushr (8 * it)).toByte() }
    }

    fun bytes(data: ByteArray) {
        ensure(data.size)
        data.copyInto(buffer, size)
        size += data.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}
