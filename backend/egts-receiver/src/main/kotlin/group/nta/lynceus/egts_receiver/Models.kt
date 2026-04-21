package group.nta.lynceus.egts_receiver

// Типы пакетов транспортного уровня
const val EGTS_PT_RESPONSE: Byte = 0
const val EGTS_PT_APPDATA: Byte = 1

// Сервисы
const val EGTS_AUTH_SERVICE: Byte = 1
const val EGTS_TELEDATA_SERVICE: Byte = 2
const val EGTS_COMMANDS_SERVICE: Byte = 4

// Коды результатов обработки
const val EGTS_PC_OK: Byte = 0
const val EGTS_PC_ERROR: Byte = 1
// ... при необходимости добавить остальные

// Заголовок транспортного уровня (11 байт без опциональных полей, если RTE=0)
data class EgtsHeader(
    val protocolVersion: Byte,          // PRV
    val securityKeyId: Byte,            // SKID
    val prefix: Byte,                   // PRF + RTE + ENA + CMP + PR
    val headerLength: Short,            // HL
    val headerEncoding: Byte,           // HE
    val frameDataLength: Int,           // FDL (USHORT)
    val packetId: Int,                  // PID (USHORT)
    val packetType: Byte,               // PT
    // Опциональные поля (если RTE=1) – в текущей реализации не используются
    val headerCheckSum: Byte            // HCS
)

// Запись уровня поддержки услуг (SDR)
data class ServiceDataRecord(
    val recordLength: Int,              // RL
    val recordNumber: Int,              // RN
    val recordFlags: Byte,              // RFL
    val objectId: Int?,                 // OID (UINT32)
    val eventId: Int?,                  // EVID (UINT32)
    val time: Int?,                     // TM (UINT32) – секунды с 01.01.2010 UTC
    val sourceServiceType: Byte,        // SST
    val recipientServiceType: Byte,     // RST
    val subrecords: List<Subrecord>     // RD (список подзаписей)
)

// Подзапись (RD)
data class Subrecord(
    val type: Byte,                     // SRT
    val data: ByteArray                 // SRD (без поля длины, т.к. длина уже в SRL)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Subrecord
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

// Пакет уровня поддержки услуг (содержимое SFRD для EGTS_PT_APPDATA)
data class ServiceFrame(
    val records: List<ServiceDataRecord>
)

// Ответный пакет EGTS_PT_RESPONSE
data class EgtsResponse(
    val responsePacketId: Int,          // RPID
    val processingResult: Byte,         // PR
    val records: List<ServiceDataRecord> = emptyList()
)