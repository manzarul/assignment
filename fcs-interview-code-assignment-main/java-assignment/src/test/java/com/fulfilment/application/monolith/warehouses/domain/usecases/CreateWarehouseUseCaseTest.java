package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.Assertions;

public class CreateWarehouseUseCaseTest {

  @Mock private WarehouseStore warehouseStore;
  @Mock private WarehouseValidationService validationService;

  private CreateWarehouseUseCase createWarehouseUseCase;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    createWarehouseUseCase = new CreateWarehouseUseCase(warehouseStore, validationService);
  }

  @Test
  public void testCreateWhenAllValidationsPassShouldSetTimestampsAndPersist() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.099";
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 30;
    warehouse.stock = 10;

    Location location = new Location("ZWOLLE-001", 1, 40);
    when(validationService.validateLocation("ZWOLLE-001")).thenReturn(location);

    // when
    createWarehouseUseCase.create(warehouse);

    // then: validations were invoked in the expected order/combination
    verify(validationService, times(1)).validateBusinessUnitCodeIsFree("MWH.099");
    verify(validationService, times(1)).validateLocation("ZWOLLE-001");
    verify(validationService, times(1))
        .validateCreationFeasibilityAndCapacity(warehouse, location);

    // and: the warehouse was stamped as newly created / active, then persisted
    assertNotNull(warehouse.createdAt);
    assertNull(warehouse.archivedAt);

    ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
    verify(warehouseStore, times(1)).create(captor.capture());
    Assertions.assertEquals("MWH.099", captor.getValue().businessUnitCode);
  }

  @Test
  public void testCreateWhenBusinessUnitCodeAlreadyExistsShouldNotPersist() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.001";
    warehouse.location = "ZWOLLE-001";

    doThrow(new WebApplicationException("Business unit code already exists.", 409))
        .when(validationService)
        .validateBusinessUnitCodeIsFree("MWH.001");

    // when / then
    WebApplicationException exception =
        Assertions.assertThrows(
            WebApplicationException.class, () -> createWarehouseUseCase.create(warehouse));

    Assertions.assertEquals(409, exception.getResponse().getStatus());

    // and: nothing downstream of the failed validation should run
    verify(validationService, never()).validateLocation(anyString());
    verify(validationService, never())
        .validateCreationFeasibilityAndCapacity(any(Warehouse.class), any(Location.class));
    verify(warehouseStore, never()).create(any(Warehouse.class));
    assertNull(warehouse.createdAt);
  }

  @Test
  public void testCreateWhenLocationIsInvalidShouldNotPersist() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.099";
    warehouse.location = "UNKNOWN-LOCATION";

    doThrow(new WebApplicationException("Location is not valid.", 422))
        .when(validationService)
        .validateLocation("UNKNOWN-LOCATION");

    // when / then
    WebApplicationException exception =
        Assertions.assertThrows(
            WebApplicationException.class, () -> createWarehouseUseCase.create(warehouse));

    Assertions.assertEquals(422, exception.getResponse().getStatus());

    verify(validationService, never())
        .validateCreationFeasibilityAndCapacity(any(Warehouse.class), any(Location.class));
    verify(warehouseStore, never()).create(any(Warehouse.class));
    assertNull(warehouse.createdAt);
  }

  @Test
  public void testCreateWhenCapacityOrFeasibilityValidationFailsShouldNotPersist() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.099";
    warehouse.location = "ZWOLLE-001";
    warehouse.capacity = 999;
    warehouse.stock = 500;

    Location location = new Location("ZWOLLE-001", 1, 40);
    when(validationService.validateLocation("ZWOLLE-001")).thenReturn(location);
    doThrow(new WebApplicationException("Capacity exceeds location maximum.", 422))
        .when(validationService)
        .validateCreationFeasibilityAndCapacity(eq(warehouse), eq(location));

    // when / then
    WebApplicationException exception =
        Assertions.assertThrows(
            WebApplicationException.class, () -> createWarehouseUseCase.create(warehouse));

    Assertions.assertEquals(422, exception.getResponse().getStatus());
    verify(warehouseStore, never()).create(any(Warehouse.class));
    assertNull(warehouse.createdAt);
  }
}