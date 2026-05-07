package com.lynceus.telemetry_processor.repository

import com.lynceus.telemetry_processor.dto.TelemetryIntervalResponse
import com.lynceus.telemetry_processor.entity.TelemetryPacket
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Tuple
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.LocalDateTime

@Repository
interface TelemetryPacketRepository : JpaRepository<TelemetryPacket, Long>, TelemetryPacketRepositoryCustom {
    // Custom query methods can be added here
    fun findByVehicleId(vehicleId: Long): List<TelemetryPacket>
}

interface TelemetryPacketRepositoryCustom {
    /**
     * Найти геозоны, у которых s2_key попадает в хотя бы один из интервалов
     * Каждый интервал задается парой (min, max)
     */
    fun findByS2KeyInRanges(
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime,
        ranges: List<Pair<Long, Long>>): List<TelemetryIntervalResponse>

    /**
     * Найти навигационные данные для устройства в интервале
     */
    fun getDeviceTelemetryInPeriod(
        deviceId: Long,
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime
    ): List<TelemetryPacket>
}

class TelemetryPacketRepositoryImpl : TelemetryPacketRepositoryCustom {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByS2KeyInRanges(
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime,
        ranges: List<Pair<Long, Long>>): List<TelemetryIntervalResponse> {
        if (ranges.isEmpty()) {
            return emptyList()
        }

        // Строим динамический WHERE clause
        val whereClause = List(ranges.size) { index ->
            "(g.s2_cell BETWEEN :min$index AND :max$index)"
        }.joinToString(" OR ")

        val sql = "SELECT g.vehicle_id, g.device_id, MIN(packet_time) as from_time, MAX(packet_time) as to_time FROM telemetry_packets g WHERE ($whereClause) " +
                " and discretized_packed_time BETWEEN :startTime AND :endTime " +
                " group by vehicle_id, device_id"

        val query = entityManager.createNativeQuery(
            sql, Tuple::class.java)

        query.setParameter("startTime", fromDateTime)
        query.setParameter("endTime", toDateTime)

        // Устанавливаем параметры для каждого интервала
        ranges.forEachIndexed { index, (min, max) ->
            query.setParameter("min$index", min)
            query.setParameter("max$index", max)
        }

        val res = query.resultList.map {

            val tuple = it as Tuple
            TelemetryIntervalResponse(
                vehicleId = tuple.get(
                    "vehicle_id",
                    Number::class.java).toLong(),
                deviceId = tuple.get(
                    "device_id",
                    Number::class.java).toLong(),
                fromDateTime = tuple.get(
                    "from_time",
                    Timestamp::class.java).toLocalDateTime(),
                toDateTime = tuple.get(
                    "to_time",
                    Timestamp::class.java).toLocalDateTime()
            )
        }

        return res
    }

    override fun getDeviceTelemetryInPeriod(
        deviceId: Long,
        fromDateTime: LocalDateTime,
        toDateTime: LocalDateTime): List<TelemetryPacket> {

        val sql = """
            SELECT * from telemetry_packets p 
            where device_id = :device_id
                and packet_time >= :from_time
                and packet_time <= :to_time
        """.trimIndent()

        val query = entityManager.createNativeQuery(
            sql, TelemetryPacket::class.java)

        query.setParameter("device_id", deviceId)
        query.setParameter("from_time", fromDateTime)
        query.setParameter("to_time", toDateTime)

        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<TelemetryPacket>
    }
}