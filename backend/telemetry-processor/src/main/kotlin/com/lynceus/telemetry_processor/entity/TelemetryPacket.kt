package com.lynceus.telemetry_processor.entity

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import java.time.LocalDateTime

@Entity
@Table(name = "telemetry_packets")
data class TelemetryPacket(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "vehicle_id", nullable = false)
    val vehicleId: Long = 0,

    @Column(name = "device_id", nullable = false)
    val deviceId: Long = 0,

    @Column(name = "packet_time", nullable = false)
    val packetTime: LocalDateTime = LocalDateTime.MIN,

    @Column(name = "reception_time", nullable = false)
    val receptionTime: LocalDateTime = LocalDateTime.MIN,

    @Column(name = "discretized_packed_time", nullable = false)
    val discretizedPackedTime: LocalDateTime = LocalDateTime.MIN,

    @Column(name = "latitude")
    val latitude: Double = 0.0,

    @Column(name = "longitude")
    val longitude: Double = 0.0,

    @Column(name = "s2_cell", length = 20)
    val s2Cell: Long = 0,

    @Column(name = "azimuth", length = 20)
    val azimuth: Short = 0
) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            other.javaClass
        val thisEffectiveClass =
            this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as TelemetryPacket

        return id != null && id == other.id
    }

    final override fun hashCode(): Int = javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , vehicleId = $vehicleId , deviceId = $deviceId , packetTime = $packetTime , receptionTime = $receptionTime , latitude = $latitude , longitude = $longitude , s2Cell = $s2Cell , azimuth = $azimuth )"
    }

}