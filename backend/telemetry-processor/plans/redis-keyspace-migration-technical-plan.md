# Redis Keyspace Notifications Migration - Технический план изменений

## Обзор
Миграция с Redis pub/sub на keyspace notifications для упрощения архитектуры и повышения надежности.

## Текущее состояние
- Redis уже настроен с KEgx (keyspace notifications включены)
- Используются два канала pub/sub: `cell.updates` и `nav.updates`
- `TelemetryWebSocketHandler` отслеживает подписки по cellId и deviceId
- `S2RegionTelemetryProcessor` публикует события при обновлениях

## Целевое состояние
- Удалить все pub/sub события
- Использовать одну подписку на `__keyevent@0__:set` для ключей `nav:*:last`
- Фильтровать обновления по s2Cell на стороне приложения
- Удалить отслеживание deviceId, оставить только cellId подписки

## Детальные изменения

### 1. TelemetryWebSocketHandler.kt

#### Удалить:
1. `sessionDeviceSubscriptions` - больше не нужно отслеживать deviceId
2. Импорт `CellUpdateEvent` и `TelemetryUpdateEvent`
3. Подписки на каналы `CHANNEL_CELL_UPDATES` и `CHANNEL_TELEMETRY_UPDATES` в `init` блоке
4. Метод `handleCellUpdate(event: CellUpdateEvent)`
5. Метод `handleTelemetryUpdate(event: TelemetryUpdateEvent)`
6. Обработку `CellUpdateEvent` в `handleRedisMessage`

#### Изменить:
1. `handleRedisMessage` - обрабатывать только keyspace notifications
2. `handleSubscribePolygon` - не сохранять deviceId подписки, только cellId
3. Логику отправки начальных данных - получать данные из Redis и фильтровать по s2Cell

#### Добавить:
1. Подписку на pattern `__keyevent@0__:set` через `RedisMessageListenerContainer`
2. Метод `handleKeyspaceNotification(key: String)` для обработки уведомлений
3. Метод `filterByS2Cell(deviceId: Long, session: WebSocketSession): Boolean` для фильтрации
4. Константу `KEYSPACE_PATTERN = "__keyevent@0__:set"`

### 2. S2RegionTelemetryProcessor.kt

#### Удалить:
1. Методы `publishCellUpdate()` и `publishTelemetryUpdate()`
2. Константы `CHANNEL_CELL_UPDATES` и `CHANNEL_TELEMETRY_UPDATES`
3. Все вызовы `redisTemplate.convertAndSend()`
4. Импорт событий `CellUpdateEvent` и `TelemetryUpdateEvent`

#### Сохранить:
1. Работу с `cell:*:devices` sets (для начальной загрузки)
2. Работу с `nav:*:last` keys (триггерит keyspace notifications)
3. Логику обновления s2Cell и deviceId маппингов

### 3. RedisEvent.kt (опционально)
- Можно удалить классы событий или оставить для обратной совместимости
- Рекомендуется оставить, так как они могут использоваться в других местах

### 4. WebSocketConfig.kt
- Проверить, не требуется ли обновление конфигурации для новой логики подписок
- Скорее всего изменений не требуется

### 5. Тестирование
1. **Unit тесты** для новой логики фильтрации по s2Cell
2. **Интеграционные тесты** с Redis keyspace notifications
3. **Тесты производительности** для проверки нагрузки

## Последовательность реализации

### Фаза 1: Подготовка кода
1. Создать backup текущего кода
2. Проанализировать зависимости и импорты
3. Подготовить тестовое окружение

### Фаза 2: Рефакторинг TelemetryWebSocketHandler
1. Удалить `sessionDeviceSubscriptions` и связанную логику
2. Реализовать подписку на keyspace notifications
3. Реализовать фильтрацию по s2Cell
4. Обновить `handleSubscribePolygon` для работы без deviceId подписок

### Фаза 3: Рефакторинг S2RegionTelemetryProcessor
1. Удалить публикацию событий
2. Убедиться, что обновление `nav:*:last` продолжает работать
3. Проверить корректность работы с `cell:*:devices`

### Фаза 4: Интеграция и тестирование
1. Написать unit-тесты для новой логики
2. Провести интеграционное тестирование
3. Ручное тестирование с WebSocket клиентом
4. Проверить производительность и нагрузку

## Риски и митигация

### Риск 1: Потеря real-time обновлений
- **Митигация**: Тщательное тестирование keyspace notifications
- **Откат**: Восстановить pub/sub код

### Риск 2: Увеличение нагрузки на Redis
- **Митигация**: Мониторинг метрик производительности
- **Откат**: Вернуться к предыдущей архитектуре

### Риск 3: Ошибки фильтрации по s2Cell
- **Митигация**: Подробное логирование и unit-тесты
- **Откат**: Добавить fallback на deviceId фильтрацию

## Метрики для мониторинга
1. Количество keyspace notifications в секунду
2. Время обработки `handleKeyspaceNotification`
3. Количество WebSocket соединений
4. Количество отправленных сообщений на соединение
5. Задержка между обновлением Redis и отправкой клиенту

## План отката
Если возникнут проблемы:
1. Вернуть pub/sub код в `TelemetryWebSocketHandler`
2. Восстановить публикацию событий в `S2RegionTelemetryProcessor`
3. Отключить keyspace notifications в Redis (если необходимо)

## Ожидаемые преимущества
1. Упрощение кода на ~60 строк
2. Удаление сложной логики отслеживания состояний
3. Автоматические уведомления от Redis
4. Более точная фильтрация по актуальным данным
5. Расшаривание потока уведомлений между клиентами

## Сроки реализации
- Подготовка: 1 день
- Рефакторинг: 2 дня
- Тестирование: 1 день
- Развертывание: 1 день

## Ответственные
- Разработчик: Реализация изменений
- Тестировщик: Проверка функциональности
- DevOps: Настройка мониторинга и развертывание