package com.lynceus.spatio_temporal.geozones.repository

import com.lynceus.spatio_temporal.geozones.entity.Device
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

@Repository
interface DeviceRepository : JpaRepository<Device, Long>, DeviceRepositoryCustom {

    /**
     * Найти устройство по ID
     */
    @Query("SELECT d FROM Device d WHERE d.id = :id")
    fun findByIdOrNull(@Param("id") id: Long): Device?

    /**
     * Найти устройство по deviceId
     */
    @Query("SELECT d FROM Device d WHERE d.deviceId = :deviceId")
    fun findByDeviceIdOrNull(@Param("deviceId") deviceId: String): Device?
}

interface DeviceRepositoryCustom {
    /**
     * Получить страницу устройств с фильтрацией
     *
     * @param nameFilter Фильтр по названию (частичное совпадение)
     * @param registrationNumberFilter Фильтр по регистрационному номеру
     * @param typeIdFilter Фильтр по типу устройства
     * @param departmentIdFilter Фильтр по отделу
     * @param pageable Пагинация
     * @return Страница с устройствами и общей информацией о пагинации
     */
    fun findWithFilters(
        nameFilter: String?,
        registrationNumberFilter: String?,
        typeIdFilter: Int?,
        departmentIdFilter: Int?,
        pageable: Pageable
    ): List<Device>
}

class DeviceRepositoryImpl : DeviceRepositoryCustom {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findWithFilters(
        nameFilter: String?,
        registrationNumberFilter: String?,
        typeIdFilter: Int?,
        departmentIdFilter: Int?,
        pageable: Pageable
    ) = buildQuery(nameFilter, registrationNumberFilter, typeIdFilter, departmentIdFilter, pageable) {
        it.setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)
    }

    private fun buildQuery(
        nameFilter: String?,
        registrationNumberFilter: String?,
        typeIdFilter: Int?,
        departmentIdFilter: Int?,
        pageable: Pageable,
        pagination: (jakarta.persistence.Query) -> jakarta.persistence.Query
    ): List<Device> {
        val whereClauses = mutableListOf<String>()
        val parameters = mutableMapOf<String, Any?>()

        if (!nameFilter.isNullOrEmpty()) {
            whereClauses.add("LOWER(d.name) LIKE LOWER(:name)")
            parameters["name"] = "%$nameFilter%"
        }

        if (!registrationNumberFilter.isNullOrEmpty()) {
            whereClauses.add("LOWER(d.registrationNumber) LIKE LOWER(:registrationNumber)")
            parameters["registrationNumber"] = "%$registrationNumberFilter%"
        }

        if (typeIdFilter != null && typeIdFilter > 0) {
            whereClauses.add("d.typeId = :typeId")
            parameters["typeId"] = typeIdFilter
        }

        if (departmentIdFilter != null && departmentIdFilter > 0) {
            whereClauses.add("d.departmentId = :departmentId")
            parameters["departmentId"] = departmentIdFilter
        }

        val baseQuery = StringBuilder("SELECT DISTINCT d FROM Device d")
        
        if (whereClauses.isNotEmpty()) {
            baseQuery.append(" WHERE ").append(whereClauses.joinToString(" AND "))
        }
        
        // Добавляем сортировку из pageable
        if (pageable.sort.isSorted) {
            baseQuery.append(" ORDER BY ")
            val orders = pageable.sort.map { order ->
                val property = order.property
                // Map property names if needed
                val mappedProperty = when (property) {
                    "registrationNumber" -> "d.registrationNumber"
                    "typeId" -> "d.typeId"
                    "departmentId" -> "d.departmentId"
                    "createdAt" -> "d.createdAt"
                    else -> "d.$property"
                }
                "$mappedProperty ${order.direction.name}"
            }
            baseQuery.append(orders.joinToString(", "))
        } else {
            // Сортировка по умолчанию (по createdAt descending, затем по id)
            baseQuery.append(" ORDER BY d.createdAt DESC, d.id DESC")
        }
        
        val q = baseQuery.toString()

        val query = entityManager.createQuery(q, Device::class.java)

        parameters.forEach { (key, value) ->
            if (value != null) {
                query.setParameter(key, value)
            }
        }

        return pagination(query).resultList as List<Device>
    }
}
