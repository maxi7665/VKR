package com.lynceus.spatio_temporal.geozones.repository

import com.lynceus.spatio_temporal.geozones.entity.Geozone
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GeozoneRepository : JpaRepository<Geozone, Long>, GeozoneRepositoryCustom {
    
    /**
     * Найти геозону по идентификатору
     */
    @Query("SELECT g FROM Geozone g WHERE g.id = :id")
    fun findByIdOrNull(@Param("id") id: Long): Geozone?
}

interface GeozoneRepositoryCustom {
    /**
     * Найти геозоны, у которых s2_key попадает в хотя бы один из интервалов
     * Каждый интервал задается парой (min, max)
     */
    fun findByS2KeyInRanges(ranges: List<Pair<Long, Long>>): List<Geozone>
}

class GeozoneRepositoryImpl : GeozoneRepositoryCustom {
    
    @PersistenceContext
    private lateinit var entityManager: EntityManager
    
    override fun findByS2KeyInRanges(ranges: List<Pair<Long, Long>>): List<Geozone> {
        if (ranges.isEmpty()) {
            return emptyList()
        }
        
        // Строим динамический WHERE clause
        val whereClause = ranges.mapIndexed { index, _ ->
            "(g.s2_key BETWEEN :min$index AND :max$index)"
        }.joinToString(" OR ")
        
        val sql = "SELECT g.* FROM geozones g WHERE $whereClause"
        
        val query = entityManager.createNativeQuery(sql, Geozone::class.java)
        
        // Устанавливаем параметры для каждого интервала
        ranges.forEachIndexed { index, (min, max) ->
            query.setParameter("min$index", min)
            query.setParameter("max$index", max)
        }
        
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<Geozone>
    }
}
