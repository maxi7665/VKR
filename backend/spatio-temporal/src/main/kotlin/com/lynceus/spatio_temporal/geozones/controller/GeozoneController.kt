package com.lynceus.spatio_temporal.geozones.controller

import com.lynceus.spatio_temporal.geozones.GeozoneDto
import com.lynceus.spatio_temporal.geozones.PolygonRequest
import com.lynceus.spatio_temporal.geozones.S2ConversionResult
import com.lynceus.spatio_temporal.geozones.entity.Geozone
import com.lynceus.spatio_temporal.geozones.repository.GeozoneRepository
import com.lynceus.spatio_temporal.geozones.service.RectangleRequest
import com.lynceus.spatio_temporal.geozones.service.S2GeometryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@RestController
@RequestMapping("/api/geozones")
@Tag(name = "Geozone Controller", description = "API для работы с геозонами")
class GeozoneController(
    private val geozoneRepository: GeozoneRepository,
    private val s2GeometryService: S2GeometryService
) {

    companion object {
        // Уровень S2, на котором хранятся geozones.s2_key
        const val S2_ZONE_KEY_LEVEL = 18
    }

    private val mapper = jacksonObjectMapper()

    /**
     * Получить геозону по идентификатору
     */
    @GetMapping("/{id}")
    @Operation(summary = "Получить геозону по ID", description = "Возвращает геозону по её уникальному идентификатору")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Геозона найдена"),
        ApiResponse(responseCode = "404", description = "Геозона не найдена")
    ])
    fun getGeozoneById(
        @Parameter(description = "Уникальный идентификатор геозоны", required = true)
        @PathVariable id: Long
    ): ResponseEntity<GeozoneDto> {
        val geozone = geozoneRepository.findByIdOrNull(id)
        
        return if (geozone != null) {
            ResponseEntity.ok(GeozoneDto.fromGeozone(geozone))
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    /**
     * Получить все геозоны
     */
    @GetMapping
    @Operation(summary = "Получить все геозоны", description = "Возвращает список всех геозон, имеющихся в базе данных")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Список геозон успешно получен")
    ])
    fun getAllGeozones(): ResponseEntity<List<GeozoneDto>> {
        val geozones = geozoneRepository.findAll()
        val ret = geozones.map { GeozoneDto.fromGeozone(it) }
        return ResponseEntity.ok(ret)
    }
    
    /**
     * Получить геозоны в прямоугольной области
     * Прямоугольник задается двумя точками: левый верхний и правый нижний угол
     */
    @PostMapping("/rectangle")
    @Operation(summary = "Получить геозоны в прямоугольной области", description = "Возвращает список геозон, находящихся в заданной прямоугольной области")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Список геозон успешно получен")
    ])
    fun getGeozonesInRectangle(
        @Parameter(description = "Параметры прямоугольной области", required = true)
        @RequestBody request: RectangleRequest
    ): ResponseEntity<List<GeozoneDto>> {
        // Рассчитываем S2 интервалы для прямоугольника с фиксированным минимальным уровнем
        val s2Ranges = s2GeometryService.rectangleToS2Ranges(
            request.topLeftLat,
            request.topLeftLon,
            request.bottomRightLat,
            request.bottomRightLon,
            S2_ZONE_KEY_LEVEL
        )
        
        // Если нет интервалов, возвращаем пустой список
        if (s2Ranges.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }
        
        // Получаем геозоны по рассчитанным S2 интервалам
        val geozones = geozoneRepository.findByS2KeyInRanges(s2Ranges)

        val ret = geozones.map { GeozoneDto.fromGeozone(it) }
        
        return ResponseEntity.ok(ret)
    }

    /**
     * Создать новую геозону
     */
    @PostMapping
    @Operation(summary = "Создать новую геозону", description = "Создает новую геозону на основе переданных данных")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Геозона успешно создана"),
        ApiResponse(responseCode = "400", description = "Некорректные данные")
    ])
    fun createGeozone(
        @Parameter(description = "Данные для создания геозоны", required = true)
        @RequestBody dto: GeozoneDto
    ): ResponseEntity<GeozoneDto> {
        val geozone = Geozone().apply {
            name = dto.name
            type = dto.type
            coordinates = mapper.writeValueAsString(dto.coordinates)  // Сериализуем List<List<Double>> в JSON строку
            isActive = dto.isActive
            s2Key = dto.s2Key
            lat = dto.lat
            lon = dto.lon
        }
        
        val savedGeozone = geozoneRepository.save(geozone)
        return ResponseEntity.status(201).body(GeozoneDto.fromGeozone(savedGeozone))
    }
    
    /**
     * Преобразовать полигон в S2 ячейки с bounding box
     */
    @PostMapping("/polygon/s2")
    @Operation(summary = "Преобразовать полигон в S2 ячейки", description = "Принимает список координат полигона и возвращает список S2 ячеек с границами для отрисовки")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Успешно преобразовано"),
        ApiResponse(responseCode = "400", description = "Некорректные координаты")
    ])
    fun convertPolygonToS2(
        @Parameter(description = "Запрос с координатами полигона и уровнем S2", required = true)
        @RequestBody request: PolygonRequest
    ): ResponseEntity<List<S2ConversionResult>> {
        val maxLevel = request.maxLevel ?: S2_ZONE_KEY_LEVEL
        
        val results = s2GeometryService.polygonToS2Results(
            request.coordinates,
            maxLevel
        )
        
        return ResponseEntity.ok(results)
    }
}
