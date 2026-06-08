# Service Package Test Coverage Plan

## Overview
This document outlines the test coverage plan for achieving full test coverage of the `com.lynceus.telemetry_processor.service` package. The package contains 4 service classes that need comprehensive testing.

## Current Coverage Status
- **S2RegionTelemetryProcessor**: Has existing tests (~335 lines) but needs enhancement
- **GeozoneService**: No tests exist
- **RedisTelemetryStorage**: No tests exist  
- **TelemetryQueryService**: No tests exist

## Test Strategy
- **Unit Tests**: Mock dependencies (RedisTemplate, RestTemplate, Repository)
- **Integration Tests**: For complex S2 geometry operations
- **Test Framework**: JUnit 5, Mockito, Spring Boot Test
- **Coverage Target**: 100% line coverage, 90%+ branch coverage

## Detailed Test Plans

### 1. GeozoneService Tests
**Test Class**: `GeozoneServiceTest`
**Dependencies to Mock**: `RestTemplate`, `GeozoneProperties`

**Test Cases**:
1. `getAllGeozones_success()` - Successful HTTP response with geozones array
2. `getAllGeozones_emptyResponse()` - HTTP returns null/empty array
3. `getAllGeozones_httpException()` - RestClientException handling
4. `getActiveGeozones_filtering()` - Filters only active geozones
5. `getActiveGeozones_emptyList()` - No active geozones
6. `getGeozoneById_found()` - Returns specific geozone
7. `getGeozoneById_notFound()` - Returns null for non-existent ID
8. `getGeozoneById_emptyList()` - No geozones available

**Edge Cases**:
- URL construction from properties
- JSON deserialization errors
- Network timeout scenarios

### 2. RedisTelemetryStorage Tests
**Test Class**: `RedisTelemetryStorageTest`
**Dependencies to Mock**: `RedisTemplate<String, Any>`, `ValueOperations`, `Pipeline`

**Test Cases**:
1. `save_success()` - Single packet saved successfully
2. `save_exception()` - Redis exception handling with logging
3. `saveAll_emptyList()` - Empty list does nothing
4. `saveAll_success()` - Multiple packets via pipeline
5. `saveAll_exception()` - Pipeline exception handling
6. `buildKey_correctFormat()` - Private method via reflection or public test
7. `get_found()` - Returns packet from Redis
8. `get_notFound()` - Returns null for non-existent key
9. `get_wrongType()` - Cast exception handling
10. `delete_success()` - Returns true on successful deletion
11. `delete_failure()` - Returns false on failure
12. `exists_true()` - Key exists in Redis
13. `exists_false()` - Key doesn't exist

**Edge Cases**:
- Key generation with different timezone conversions
- Redis connection failures
- Serialization/deserialization issues

### 3. TelemetryQueryService Tests
**Test Class**: `TelemetryQueryServiceTest`
**Dependencies to Mock**: `TelemetryPacketRepository`, `NavigationProcessor` constants

**Test Cases**:
1. `findTelemetryIntervals_basicPolygon()` - Simple polygon query
2. `findTelemetryIntervals_emptyPolygon()` - Empty polygon list
3. `findTelemetryIntervals_complexPolygon()` - Multiple S2 cells
4. `findTelemetryIntervals_repositoryReturnsData()` - Repository integration
5. `getDeviceTelemetryInPeriod_success()` - Basic device query
6. `getDeviceTelemetryInPeriod_emptyResult()` - No data in period
7. `mergeAdjacentRanges_basic()` - Merges overlapping ranges
8. `mergeAdjacentRanges_adjacent()` - Merges adjacent ranges (difference of 1)
9. `mergeAdjacentRanges_separate()` - Keeps separate ranges
10. `mergeAdjacentRanges_empty()` - Empty input list
11. `mergeAdjacentRanges_single()` - Single range unchanged

**Edge Cases**:
- S2 geometry library exceptions
- Invalid polygon coordinates
- Large polygon with many S2 cells (>200 maxCells limit)

### 4. S2RegionTelemetryProcessor Test Enhancements
**Existing Coverage**: Good but needs completion
**Test Class**: `S2RegionTelemetryProcessorTest` (enhance existing)

**Additional Test Cases Needed**:
1. `loadCellsDevices_success()` - @PostConstruct method loading
2. `loadCellsDevices_emptyRedis()` - No cell keys in Redis
3. `loadCellsDevices_invalidKeyFormat()` - Malformed Redis keys
4. `processPacket_exceptionInRedis()` - Redis operation exceptions
5. `processPacket_jsonSerializationError()` - ObjectMapper exception
6. `processPacket_concurrentModification()` - Thread safety with @Synchronized
7. Edge cases for `deviceIdToS2Key` and `s2keyToDeviceIdSet` state transitions

## Test Implementation Priorities

### Phase 1: Critical Business Logic
1. RedisTelemetryStorage - Data persistence layer
2. S2RegionTelemetryProcessor - Core business logic
3. GeozoneService - External integration

### Phase 2: Query and Utility Logic
1. TelemetryQueryService - Complex S2 queries
2. Edge cases and error handling

## Test Data Setup
- Use `TelemetryPacket` factory methods for consistent test data
- Mock external dependencies to isolate unit tests
- Use reflection for testing private methods where necessary
- Consider creating test utility classes for common setup

## Coverage Verification
1. Run `./gradlew test jacocoTestReport` after each test implementation
2. Check coverage reports in `build/reports/jacoco/test/html/`
3. Aim for minimum coverage thresholds:
   - Line coverage: 100%
   - Branch coverage: 90%+
   - Method coverage: 100%

## Success Criteria
- All 4 service classes have comprehensive test suites
- Jacoco report shows 100% line coverage for service package
- Tests pass consistently in CI/CD pipeline
- Edge cases and error conditions are properly tested
- Test code follows project coding standards

## Estimated Test Count
- GeozoneService: 8-10 tests
- RedisTelemetryStorage: 13-15 tests  
- TelemetryQueryService: 11-13 tests
- S2RegionTelemetryProcessor: 7-10 additional tests
- **Total**: 39-48 new tests

## Next Steps
1. Implement GeozoneServiceTest
2. Implement RedisTelemetryStorageTest
3. Implement TelemetryQueryServiceTest
4. Enhance S2RegionTelemetryProcessorTest
5. Run coverage verification
6. Optimize and refactor tests as needed