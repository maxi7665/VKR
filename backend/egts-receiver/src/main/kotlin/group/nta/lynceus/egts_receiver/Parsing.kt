package group.nta.lynceus.egts_receiver

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.ByteOrder

fun ByteBuf.readEgtsPacket(): Pair<EgtsHeader?, ServiceFrame?>? {
    if (readableBytes() < 11) return null

    markReaderIndex()
    val order = order(ByteOrder.LITTLE_ENDIAN)

    val prv = readByte()
    val skid = readByte()
    val prefix = readByte()
    val hl = readUnsignedByte().toShort()
    val he = readByte()
    val fdl = readUnsignedShortLE()
    val pid = readUnsignedShortLE()
    val pt = readByte()
    // Опциональные поля PRA, RCA, TTL не читаем, т.к. RTE = 0
    val hcs = readByte()

    // Проверка CRC-8 заголовка
    val headerBytes = ByteArray(10)
    resetReaderIndex()
    readBytes(headerBytes)
    val calcHcs = Crc8.calculate(headerBytes)
    if (calcHcs != hcs) {
        // Ошибка контрольной суммы заголовка
        resetReaderIndex()
        return null
    }

    val header = EgtsHeader(prv, skid, prefix, hl, he, fdl, pid, pt, hcs)

    // Чтение SFRD
    val sframe = if (fdl > 0) {
        val sframeBytes = ByteArray(fdl)
        readBytes(sframeBytes)

        // Проверка CRC-16 данных
        val sfrcs = readUnsignedShortLE()
        val calcSfrcs = Crc16Ccitt.calculate(sframeBytes)
        if (calcSfrcs.toInt() != sfrcs) {
            // Ошибка CRC данных
            resetReaderIndex()
            return null
        }

        when (pt) {
            EGTS_PT_APPDATA -> parseServiceFrame(sframeBytes)
            EGTS_PT_RESPONSE -> null // В ответе данные не разбираем для этого примера
            else -> null
        }
    } else null

    return header to sframe
}

private fun parseServiceFrame(data: ByteArray): ServiceFrame {
    val buf = Unpooled.wrappedBuffer(data).order(ByteOrder.LITTLE_ENDIAN)
    val records = mutableListOf<ServiceDataRecord>()

    while (buf.isReadable) {
        val rl = buf.readUnsignedShortLE()
        val rn = buf.readUnsignedShortLE()
        val rfl = buf.readByte()
        val hasOid = (rfl.toInt() and 0x01) != 0
        val hasEvid = (rfl.toInt() and 0x02) != 0
        val hasTm = (rfl.toInt() and 0x04) != 0

        val oid = if (hasOid) buf.readIntLE() else null
        val evid = if (hasEvid) buf.readIntLE() else null
        val tm = if (hasTm) buf.readIntLE() else null

        val sst = buf.readByte()
        val rst = buf.readByte()

        val subrecordsData = ByteArray(rl - (if (hasOid) 4 else 0) - (if (hasEvid) 4 else 0) - (if (hasTm) 4 else 0) - 2)
        buf.readBytes(subrecordsData)
        val subrecords = parseSubrecords(subrecordsData)

        records.add(ServiceDataRecord(rl, rn, rfl, oid, evid, tm, sst, rst, subrecords))
    }
    return ServiceFrame(records)
}

private fun parseSubrecords(data: ByteArray): List<Subrecord> {
    val buf = Unpooled.wrappedBuffer(data).order(ByteOrder.LITTLE_ENDIAN)
    val subs = mutableListOf<Subrecord>()
    while (buf.isReadable) {
        val srt = buf.readByte()
        val srl = buf.readUnsignedShortLE()
        val srd = ByteArray(srl)
        buf.readBytes(srd)
        subs.add(Subrecord(srt, srd))
    }
    return subs
}