package com.lynceus.spatio_temporal.geozones

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Запрос для преобразования полигона в S2 ячейки")
data class PolygonRequest(
    @Schema(description = "Список координат полигона, каждая точка - [широта, долгота]", 
          example = "[[55.751244, 37.618423], [55.755244, 37.622423], [55.758244, 37.615423]]",
          required = true)
    val coordinates: List<List<Double>>,
    
    @Schema(description = "Максимальный уровень S2 (по умолчанию 24)", 
          example = "24",
          required = false)
    val maxLevel: Int? = null
)
