package com.lynceus.spatio_temporal.geozones.controller

import com.lynceus.spatio_temporal.geozones.DeviceDto
import com.lynceus.spatio_temporal.geozones.entity.Device
import com.lynceus.spatio_temporal.geozones.repository.DeviceRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/devices")
@Tag(name = "Device Controller", description = "API для работы с устройствами (транспортными средствами)")
class DeviceController(
    private val deviceRepository: DeviceRepository
) {

    /**
     * Получить список устройств с фильтрацией и пагинацией
     */
    @GetMapping
    @Operation(
        summary = "Получить список устройств",
        description = "Возвращает список устройств с поддержкой фильтрации и пагинации"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Список устройств успешно получен")
    ])
    fun getDevices(
        @Parameter(description = "Фильтр по названию (частичное совпадение)")
        @RequestParam(name = "name", required = false) name: String? = null,

        @Parameter(description = "Фильтр по регистрационному номеру")
        @RequestParam(name = "registrationNumber", required = false) registrationNumber: String? = null,

        @Parameter(description = "Фильтр по типу устройства")
        @RequestParam(name = "typeId", required = false) typeId: Int? = null,

        @Parameter(description = "Фильтр по ID подразделения")
        @RequestParam(name = "departmentId", required = false) departmentId: Int? = null,

        @Parameter(description = "Номер страницы (по умолчанию 0)")
        @RequestParam(name = "page", defaultValue = "0") page: Int,

        @Parameter(description = "Размер страницы (по умолчанию 20)")
        @RequestParam(name = "size", defaultValue = "20") size: Int,

        @Parameter(description = "Сортировка (например: createdAt,id или -createdAt для убывания)")
        @RequestParam(name = "sort", defaultValue = "id:asc") sort: String
    ): ResponseEntity<List<DeviceDto>?>? {
        val pageable = buildPageable(page, size, sort)
        val devicePage = deviceRepository.findWithFilters(name, registrationNumber, typeId, departmentId, pageable)
        val dtoPage = devicePage.map { DeviceDto.fromDevice(it) }
        return ResponseEntity.ok(dtoPage)
    }

    /**
     * Получить устройство по идентификатору
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Получить устройство по ID",
        description = "Возвращает устройство по его уникальному идентификатору"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Устройство найдено"),
        ApiResponse(responseCode = "404", description = "Устройство не найдено")
    ])
    fun getDeviceById(
        @Parameter(description = "Уникальный идентификатор устройства", required = true)
        @PathVariable id: Long
    ): ResponseEntity<DeviceDto> {
        val device = deviceRepository.findByIdOrNull(id)
        return if (device != null) {
            ResponseEntity.ok(DeviceDto.fromDevice(device))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Создать новое устройство
     */
    @PostMapping
    @Operation(
        summary = "Создать новое устройство",
        description = "Создает новое устройство на основе переданных данных"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Устройство успешно создано"),
        ApiResponse(responseCode = "400", description = "Некорректные данные"),
        ApiResponse(responseCode = "409", description = "Устройство с таким deviceId уже существует")
    ])
    fun createDevice(
        @Parameter(description = "Данные для создания устройства", required = true)
        @RequestBody dto: DeviceDto
    ): ResponseEntity<DeviceDto> {
        // Проверка на уникальность deviceId
        if (deviceRepository.findByDeviceIdOrNull(dto.deviceId) != null) {
            return ResponseEntity.status(409).body(null)
        }

        val device = Device().apply {
            name = dto.name
            registrationNumber = dto.registrationNumber
            deviceId = dto.deviceId
            typeId = dto.typeId
            departmentId = dto.departmentId
        }

        val savedDevice = deviceRepository.save(device)
        return ResponseEntity.status(201).body(DeviceDto.fromDevice(savedDevice))
    }

    /**
     * Обновить существующее устройство
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Обновить устройство",
        description = "Обновляет существующее устройство на основе переданных данных"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Устройство успешно обновлено"),
        ApiResponse(responseCode = "400", description = "Некорректные данные"),
        ApiResponse(responseCode = "404", description = "Устройство не найдено"),
        ApiResponse(responseCode = "409", description = "Устройство с таким deviceId уже существует")
    ])
    fun updateDevice(
        @Parameter(description = "Уникальный идентификатор устройства", required = true)
        @PathVariable id: Long,

        @Parameter(description = "Данные для обновления устройства", required = true)
        @RequestBody dto: DeviceDto
    ): ResponseEntity<DeviceDto> {
        val existingDevice = deviceRepository.findByIdOrNull(id)
            ?: return ResponseEntity.notFound().build()

        // Проверка на уникальность deviceId для другого устройства
        deviceRepository.findByDeviceIdOrNull(dto.deviceId)?.let { otherDevice ->
            if (otherDevice.id != id) {
                return ResponseEntity.status(409).body(null)
            }
        }

        existingDevice.apply {
            name = dto.name
            registrationNumber = dto.registrationNumber
            deviceId = dto.deviceId
            typeId = dto.typeId
            departmentId = dto.departmentId
        }

        val updatedDevice = deviceRepository.save(existingDevice)
        return ResponseEntity.ok(DeviceDto.fromDevice(updatedDevice))
    }

    /**
     * Удалить устройство по идентификатору
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Удалить устройство",
        description = "Удаляет устройство по его уникальному идентификатору"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Устройство успешно удалено"),
        ApiResponse(responseCode = "404", description = "Устройство не найдено")
    ])
    fun deleteDevice(
        @Parameter(description = "Уникальный идентификатор устройства", required = true)
        @PathVariable id: Long
    ): ResponseEntity<Unit> {
        if (!deviceRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        deviceRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Построение Pageable из параметров запроса
     */
    private fun buildPageable(page: Int, size: Int, sort: String): org.springframework.data.domain.Pageable {
        val sortFields = sort.split(",").toMutableList()




        val orders = mutableListOf<Sort.Order>()

        for (pair in sortFields) {

            val splitSort = pair.split(":").toList()
            val field = splitSort.getOrElse(0) { "createdAt" }
            val sortOrder = splitSort.getOrElse(1) { "asc" }

            val order = when (sortOrder) {
                "asc" -> Sort.Order.asc(field)
                else -> Sort.Order.desc(field)
            }

            orders += order
        }

        return PageRequest.of(page, size, Sort.by(orders))
    }
}
