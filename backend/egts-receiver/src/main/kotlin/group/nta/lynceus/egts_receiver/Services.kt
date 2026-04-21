package group.nta.lynceus.egts_receiver

import io.netty.buffer.Unpooled
import java.nio.ByteOrder

interface ServiceHandler {
    fun handle(record: ServiceDataRecord): List<Subrecord> // Возвращает ответные подзаписи
}

class AuthServiceHandler : ServiceHandler {
    override fun handle(record: ServiceDataRecord): List<Subrecord> {
        println("[AUTH] Received auth request: $record")
        // Здесь должна быть проверка учётных данных
        // Возвращаем результат авторизации (EGTS_SR_RESULT_CODE = 9)
        val resultCode = EGTS_PC_OK
        return listOf(Subrecord(9, byteArrayOf(resultCode)))
    }
}

class TeleDataServiceHandler : ServiceHandler {
    override fun handle(record: ServiceDataRecord): List<Subrecord> {
        println("[TELEMETRY] Received telemetry record: $record")
        // Заглушка: просто логируем
        // Возвращаем подтверждение записи (EGTS_SR_RECORD_RESPONSE = 0)
        return listOf(createRecordResponse(record.recordNumber, EGTS_PC_OK))
    }
}

class CommandsServiceHandler : ServiceHandler {
    override fun handle(record: ServiceDataRecord): List<Subrecord> {
        println("[COMMANDS] Received command record: $record")
        // Заглушка: логируем
        return listOf(createRecordResponse(record.recordNumber, EGTS_PC_OK))
    }
}

fun createRecordResponse(recordNumber: Int, status: Byte): Subrecord {
    val buf = Unpooled.buffer(3).order(ByteOrder.LITTLE_ENDIAN)
    buf.writeShortLE(recordNumber)
    buf.writeByte(status.toInt())
    return Subrecord(0, buf.array())
}