package group.nta.lynceus.egts_receiver

object Crc8 {
    private const val POLY = 0x9B
    private val table = ByteArray(256)

    init {
        for (i in 0..255) {
            var crc = i
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    (crc shl 1) xor POLY
                } else {
                    crc shl 1
                }
            }
            table[i] = (crc and 0xFF).toByte()
        }
    }

    fun calculate(data: ByteArray): Byte {
        var crc = 0xFF
        for (b in data) {
            val idx = (crc xor b.toInt()) and 0xFF
            crc = table[idx].toInt() and 0xFF
        }
        return crc.toByte()
    }
}

object Crc16Ccitt {
    private const val POLY = 0x1021
    private val table = UShortArray(256)

    init {
        for (i in 0..255) {
            var crc = (i shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    (crc shl 1) xor POLY
                } else {
                    crc shl 1
                }
            }
            table[i] = (crc and 0xFFFF).toUShort()
        }
    }

    fun calculate(data: ByteArray): UShort {
        var crc = 0xFFFF
        for (b in data) {
            val idx = ((crc shr 8) xor b.toInt()) and 0xFF
            crc = ((crc shl 8) and 0xFFFF) xor table[idx].toInt()
        }
        return crc.toUShort()
    }
}