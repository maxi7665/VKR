package group.nta.lynceus.egts_receiver

import java.nio.ByteOrder

fun buildEgtsResponse(
    requestPacketId: Int,
    processingResult: Byte,
    responseRecords: List<ServiceDataRecord>
): ByteArray {
    val sframeBytes = buildServiceFrameBytes(responseRecords)
    val header = buildHeader(
        packetType = EGTS_PT_RESPONSE,
        packetId = 1, // ID ответного пакета (можно инкрементировать)
        frameDataLength = 2 + sframeBytes.size // RPID(2) + PR(1) + записи
    )

    val buf = io.netty.buffer.Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN)
    buf.writeBytes(header)
    buf.writeShortLE(requestPacketId) // RPID
    buf.writeByte(processingResult.toInt()) // PR
    buf.writeBytes(sframeBytes)

    // CRC-16 данных
    val dataForCrc = ByteArray(2 + 1 + sframeBytes.size)
    buf.getBytes(header.size, dataForCrc)
    val sfrcs = Crc16Ccitt.calculate(dataForCrc)
    buf.writeShortLE(sfrcs.toInt())

    return buf.array()
}

private fun buildHeader(
    packetType: Byte,
    packetId: Int,
    frameDataLength: Int
): ByteArray {
    val buf = io.netty.buffer.Unpooled.buffer(11).order(ByteOrder.LITTLE_ENDIAN)
    buf.writeByte(1)          // PRV
    buf.writeByte(0)          // SKID
    buf.writeByte(0b0010_0000.toByte().toInt()) // PRF: RTE=0, ENA=0, CMP=0, PR=10 (средний)
    buf.writeByte(11)         // HL
    buf.writeByte(0)          // HE
    buf.writeShortLE(frameDataLength) // FDL
    buf.writeShortLE(packetId) // PID
    buf.writeByte(packetType.toInt()) // PT

    val headerBytes = ByteArray(10)
    buf.getBytes(0, headerBytes)
    val hcs = Crc8.calculate(headerBytes)
    buf.writeByte(hcs.toInt())

    return buf.array()
}

private fun buildServiceFrameBytes(records: List<ServiceDataRecord>): ByteArray {
    val buf = io.netty.buffer.Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN)
    for (rec in records) {
        // Упрощённо: формируем только базовые поля (OID/EVID/TM не пишем)
        // В реальном коде нужно учитывать флаги RFL
        buf.writeShortLE(rec.recordLength)
        buf.writeShortLE(rec.recordNumber)
        buf.writeByte(rec.recordFlags.toInt())
        // OID, EVID, TM не пишем (флаги 0)
        buf.writeByte(rec.sourceServiceType.toInt())
        buf.writeByte(rec.recipientServiceType.toInt())
        for (sub in rec.subrecords) {
            buf.writeByte(sub.type.toInt())
            buf.writeShortLE(sub.data.size)
            buf.writeBytes(sub.data)
        }
    }
    return buf.array()
}