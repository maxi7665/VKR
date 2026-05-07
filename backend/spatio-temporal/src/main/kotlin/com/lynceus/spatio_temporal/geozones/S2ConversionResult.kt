package com.lynceus.spatio_temporal.geozones

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Результат преобразования зоны в S2 ячейки")
data class S2ConversionResult(
    @Schema(description = "Номер S2 ячейки", example = "18446744073709551615")
    val s2CellId: Long,

    @Schema(description = "Уровень S2 ячейки (0-30)", example = "24")
    val level: Int,

    @Schema(description = "Полигон ячейки", example = "[[55.7558, 55.7558],[55.7558,55.7558]]")
    val polygon: MutableList<MutableList<Double>>
)

@Schema(description = "Границы прямоугольника в координатах")
data class BoundingBox(
    @Schema(description = "Северная граница (максимальная широта)", example = "55.7558")
    val latNorth: Double,
    
    @Schema(description = "Западная граница (минимальная долгота)", example = "37.6173")
    val lonWest: Double,
    
    @Schema(description = "Южная граница (минимальная широта)", example = "55.7512")
    val latSouth: Double,
    
    @Schema(description = "Восточная граница (максимальная долгота)", example = "37.6231")
    val lonEast: Double
)
