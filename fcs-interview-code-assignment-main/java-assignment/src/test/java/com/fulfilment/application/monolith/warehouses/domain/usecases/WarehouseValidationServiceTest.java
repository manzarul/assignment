package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link WarehouseValidationService}.
 *
 * <p>Its two collaborators ({@link WarehouseStore} and {@link LocationResolver}) are ports
 * (plain interfaces), so they're mocked directly with Mockito - no Panache or CDI context is
 * involved in this class at all.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseValidationServiceTest {

  @Mock private WarehouseStore warehouseStore;

  @Mock private LocationResolver locationResolver;

  private WarehouseValidationService validationService;

  @BeforeEach
  void setUp() {
    validationService = new WarehouseValidationService(warehouseStore, locationResolver);
  }

  private Warehouse warehouse(String buCode, String location, Integer capacity, Integer stock, LocalDateTime archivedAt) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = buCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    warehouse.archivedAt = archivedAt;
    return warehouse;
  }

  private Location location(String identification, int maxNumberOfWarehouses, int maxCapacity) {
    Location location = new Location(identification, maxNumberOfWarehouses, maxCapacity);
        return location;
  }

  // --- validateBusinessUnitCodeIsFree() ---

  @Test
  @DisplayName("validateBusinessUnitCodeIsFree() throws 422 when the code is null")
  void validateBusinessUnitCodeIsFree_whenNull_throws422() {
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateBusinessUnitCodeIsFree(null));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateBusinessUnitCodeIsFree() throws 422 when the code is blank")
  void validateBusinessUnitCodeIsFree_whenBlank_throws422() {
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateBusinessUnitCodeIsFree("   "));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateBusinessUnitCodeIsFree() throws 409 when an active warehouse already uses the code")
  void validateBusinessUnitCodeIsFree_whenActiveWarehouseExists_throws409() {
    when(warehouseStore.findByBusinessUnitCode("BU1"))
        .thenReturn(warehouse("BU1", "loc-1", 100, 10, null));

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateBusinessUnitCodeIsFree("BU1"));

    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateBusinessUnitCodeIsFree() passes when the existing warehouse with that code is archived")
  void validateBusinessUnitCodeIsFree_whenExistingIsArchived_passes() {
    when(warehouseStore.findByBusinessUnitCode("BU1"))
        .thenReturn(warehouse("BU1", "loc-1", 100, 10, LocalDateTime.now()));

    assertDoesNotThrow(() -> validationService.validateBusinessUnitCodeIsFree("BU1"));
  }

  @Test
  @DisplayName("validateBusinessUnitCodeIsFree() passes when no warehouse uses the code")
  void validateBusinessUnitCodeIsFree_whenNoneExists_passes() {
    when(warehouseStore.findByBusinessUnitCode("BU1")).thenReturn(null);

    assertDoesNotThrow(() -> validationService.validateBusinessUnitCodeIsFree("BU1"));
  }

  // --- validateLocation() ---

  @Test
  @DisplayName("validateLocation() throws 422 when the identifier is null")
  void validateLocation_whenNull_throws422() {
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> validationService.validateLocation(null));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateLocation() throws 422 when the identifier is blank")
  void validateLocation_whenBlank_throws422() {
    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> validationService.validateLocation("  "));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateLocation() throws 422 when the resolver can't find the location")
  void validateLocation_whenUnresolvable_throws422() {
    when(locationResolver.resolveByIdentifier("ZWOLLE")).thenReturn(null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> validationService.validateLocation("ZWOLLE"));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateLocation() returns the resolved location when found")
  void validateLocation_whenResolvable_returnsLocation() {
    Location resolved = location("ZWOLLE", 5, 1000);
    when(locationResolver.resolveByIdentifier("ZWOLLE")).thenReturn(resolved);

    Location result = validationService.validateLocation("ZWOLLE");

    assertSame(resolved, result);
  }

  // --- validateCreationFeasibilityAndCapacity() ---

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when the location's warehouse limit is reached")
  void validateCreationFeasibilityAndCapacity_whenMaxWarehousesReached_throws422() {
    Location location = location("ZWOLLE", 1, 1000);
    when(warehouseStore.getAll())
        .thenReturn(List.of(warehouse("BU-EXISTING", "ZWOLLE", 100, 10, null)));
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 50, 5, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() ignores archived warehouses when counting the location's warehouses")
  void validateCreationFeasibilityAndCapacity_ignoresArchivedWarehousesInCount() {
    Location location = location("ZWOLLE", 1, 1000);
    when(warehouseStore.getAll())
        .thenReturn(
            List.of(warehouse("BU-ARCHIVED", "ZWOLLE", 100, 10, LocalDateTime.now())));
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 50, 5, null);

    assertDoesNotThrow(
        () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when capacity is null")
  void validateCreationFeasibilityAndCapacity_whenCapacityNull_throws422() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll()).thenReturn(List.of());
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", null, 5, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when capacity is zero or negative")
  void validateCreationFeasibilityAndCapacity_whenCapacityNotPositive_throws422() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll()).thenReturn(List.of());
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 0, 0, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when stock is null")
  void validateCreationFeasibilityAndCapacity_whenStockNull_throws422() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll()).thenReturn(List.of());
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 50, null, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when stock is negative")
  void validateCreationFeasibilityAndCapacity_whenStockNegative_throws422() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll()).thenReturn(List.of());
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 50, -1, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when stock exceeds capacity")
  void validateCreationFeasibilityAndCapacity_whenStockExceedsCapacity_throws422() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll()).thenReturn(List.of());
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 10, 20, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() throws 422 when the location's total capacity would be exceeded")
  void validateCreationFeasibilityAndCapacity_whenLocationCapacityExceeded_throws422() {
    Location location = location("ZWOLLE", 5, 100);
    when(warehouseStore.getAll())
        .thenReturn(List.of(warehouse("BU-EXISTING", "ZWOLLE", 80, 10, null)));
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 30, 10, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() ignores archived warehouses and other locations when summing used capacity")
  void validateCreationFeasibilityAndCapacity_ignoresArchivedAndOtherLocationsInCapacitySum() {
    Location location = location("ZWOLLE", 5, 100);
    when(warehouseStore.getAll())
        .thenReturn(
            List.of(
                warehouse("BU-ARCHIVED", "ZWOLLE", 90, 10, LocalDateTime.now()),
                warehouse("BU-OTHER-LOCATION", "AMSTERDAM", 90, 10, null)));
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 30, 10, null);

    assertDoesNotThrow(
        () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));
  }

  @Test
  @DisplayName("validateCreationFeasibilityAndCapacity() passes for a valid candidate")
  void validateCreationFeasibilityAndCapacity_whenValid_passes() {
    Location location = location("ZWOLLE", 5, 1000);
    when(warehouseStore.getAll())
        .thenReturn(List.of(warehouse("BU-EXISTING", "ZWOLLE", 100, 10, null)));
    Warehouse candidate = warehouse("BU-NEW", "ZWOLLE", 50, 20, null);

    assertDoesNotThrow(
        () -> validationService.validateCreationFeasibilityAndCapacity(candidate, location));
  }

  // --- validateReplacementRules() ---

  @Test
  @DisplayName("validateReplacementRules() throws 422 when the new capacity is null")
  void validateReplacementRules_whenNewCapacityNull_throws422() {
    Warehouse previous = warehouse("BU-OLD", "ZWOLLE", 100, 50, null);
    Warehouse replacement = warehouse("BU-NEW", "ZWOLLE", null, 50, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateReplacementRules(previous, replacement));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateReplacementRules() throws 422 when the new capacity can't hold the carried-over stock")
  void validateReplacementRules_whenNewCapacityTooSmall_throws422() {
    Warehouse previous = warehouse("BU-OLD", "ZWOLLE", 100, 50, null);
    Warehouse replacement = warehouse("BU-NEW", "ZWOLLE", 49, 50, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateReplacementRules(previous, replacement));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateReplacementRules() throws 422 when the new stock is null")
  void validateReplacementRules_whenNewStockNull_throws422() {
    Warehouse previous = warehouse("BU-OLD", "ZWOLLE", 100, 50, null);
    Warehouse replacement = warehouse("BU-NEW", "ZWOLLE", 100, null, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateReplacementRules(previous, replacement));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateReplacementRules() throws 422 when the new stock doesn't match the previous stock")
  void validateReplacementRules_whenNewStockDoesNotMatch_throws422() {
    Warehouse previous = warehouse("BU-OLD", "ZWOLLE", 100, 50, null);
    Warehouse replacement = warehouse("BU-NEW", "ZWOLLE", 100, 51, null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> validationService.validateReplacementRules(previous, replacement));

    assertEquals(422, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("validateReplacementRules() passes when capacity and stock are both valid")
  void validateReplacementRules_whenValid_passes() {
    Warehouse previous = warehouse("BU-OLD", "ZWOLLE", 100, 50, null);
    Warehouse replacement = warehouse("BU-NEW", "ZWOLLE", 100, 50, null);

    assertDoesNotThrow(() -> validationService.validateReplacementRules(previous, replacement));
  }
}