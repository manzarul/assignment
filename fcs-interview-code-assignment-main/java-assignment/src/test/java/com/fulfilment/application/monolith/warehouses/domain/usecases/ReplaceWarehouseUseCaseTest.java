package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ReplaceWarehouseUseCase}.
 *
 * <p>NOTE: {@code Warehouse} is assumed to be a plain mutable model with public fields
 * (businessUnitCode, location, archivedAt, createdAt), matching how it's accessed directly
 * inside the use case under test. If your actual model uses a constructor/builder or getters
 * instead of public fields, adjust the helper {@link #warehouse(String, LocalDateTime)} method
 * accordingly.
 */
@ExtendWith(MockitoExtension.class)
class ReplaceWarehouseUseCaseTest {

  private static final String BUC_TO_REPLACE = "BU-OLD";
  private static final String NEW_BUC = "BU-NEW";

  @Mock private WarehouseStore warehouseStore;
  @Mock private WarehouseValidationService validationService;
  @Mock private Location resolvedLocation;

  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ReplaceWarehouseUseCase(warehouseStore, validationService);
  }

  private Warehouse warehouse(String businessUnitCode, LocalDateTime archivedAt) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.archivedAt = archivedAt;
    return warehouse;
  }

  @Test
  void replace_whenNoWarehouseExistsForCode_throws404AndDoesNothingElse() {
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(null);
    Warehouse newWarehouse = warehouse(NEW_BUC, null);

    WebApplicationException exception =
        assertThrows(
            WebApplicationException.class, () -> useCase.replace(BUC_TO_REPLACE, newWarehouse));

    assertEquals(404, exception.getResponse().getStatus());
    verify(warehouseStore).findByBusinessUnitCode(BUC_TO_REPLACE);
    verifyNoMoreInteractions(warehouseStore, validationService);
  }

  @Test
  void replace_whenExistingWarehouseIsAlreadyArchived_throws404AndDoesNothingElse() {
    Warehouse archivedWarehouse = warehouse(BUC_TO_REPLACE, LocalDateTime.now().minusDays(1));
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(archivedWarehouse);
    Warehouse newWarehouse = warehouse(NEW_BUC, null);

    WebApplicationException exception =
        assertThrows(
            WebApplicationException.class, () -> useCase.replace(BUC_TO_REPLACE, newWarehouse));

    assertEquals(404, exception.getResponse().getStatus());
    verify(warehouseStore).findByBusinessUnitCode(BUC_TO_REPLACE);
    verifyNoMoreInteractions(warehouseStore, validationService);
  }

  @Test
  void replace_whenReplacementRulesFail_throwsAndNeverArchivesOrCreates() {
    Warehouse previousWarehouse = warehouse(BUC_TO_REPLACE, null);
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(previousWarehouse);
    Warehouse newWarehouse = warehouse(NEW_BUC, null);

    RuntimeException validationFailure = new RuntimeException("capacity/stock mismatch");
    doThrow(validationFailure)
        .when(validationService)
        .validateReplacementRules(previousWarehouse, newWarehouse);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> useCase.replace(BUC_TO_REPLACE, newWarehouse));

    assertEquals(validationFailure, thrown);
    assertNull(previousWarehouse.archivedAt, "previous warehouse must not be archived on failure");
    verify(warehouseStore, never()).update(any());
    verify(warehouseStore, never()).create(any());
    verify(validationService, never()).validateBusinessUnitCodeIsFree(any());
    verify(validationService, never()).validateLocation(any());
    verify(validationService, never()).validateCreationFeasibilityAndCapacity(any(), any());
  }

  @Test
  void replace_whenNewBusinessUnitCodeIsNotFree_throwsAfterPreviousWasAlreadyArchived() {
    // Documents existing behavior: the outgoing warehouse is archived BEFORE the incoming
    // warehouse's rules are checked, so a failure at this stage leaves the previous warehouse
    // archived even though no replacement was created.
    Warehouse previousWarehouse = warehouse(BUC_TO_REPLACE, null);
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(previousWarehouse);
    Warehouse newWarehouse = warehouse(NEW_BUC, null);

    RuntimeException codeTaken = new RuntimeException("business unit code already in use");
    doThrow(codeTaken).when(validationService).validateBusinessUnitCodeIsFree(NEW_BUC);

    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> useCase.replace(BUC_TO_REPLACE, newWarehouse));

    assertEquals(codeTaken, thrown);
    assertNotNull(previousWarehouse.archivedAt, "previous warehouse should already be archived");
    verify(warehouseStore).update(previousWarehouse);
    verify(warehouseStore, never()).create(any());
    verify(validationService, never()).validateLocation(any());
    verify(validationService, never()).validateCreationFeasibilityAndCapacity(any(), any());
  }

  @Test
  void replace_happyPath_archivesPreviousThenValidatesAndCreatesNewWarehouseInOrder() {
    Warehouse previousWarehouse = warehouse(BUC_TO_REPLACE, null);
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(previousWarehouse);

    Warehouse newWarehouse = warehouse(NEW_BUC, LocalDateTime.now().minusDays(5));
    newWarehouse.location = "some raw location";

    when(validationService.validateLocation(newWarehouse.location)).thenReturn(resolvedLocation);

    useCase.replace(BUC_TO_REPLACE, newWarehouse);

    // previous warehouse archived
    assertNotNull(previousWarehouse.archivedAt);

    // new warehouse prepared correctly
    assertNotNull(newWarehouse.createdAt);
    assertNull(newWarehouse.archivedAt);

    InOrder inOrder = inOrder(warehouseStore, validationService);
    inOrder.verify(warehouseStore).findByBusinessUnitCode(BUC_TO_REPLACE);
    inOrder.verify(validationService).validateReplacementRules(previousWarehouse, newWarehouse);
    inOrder.verify(warehouseStore).update(previousWarehouse);
    inOrder.verify(validationService).validateBusinessUnitCodeIsFree(NEW_BUC);
    inOrder.verify(validationService).validateLocation(newWarehouse.location);
    inOrder
        .verify(validationService)
        .validateCreationFeasibilityAndCapacity(newWarehouse, resolvedLocation);
    inOrder.verify(warehouseStore).create(newWarehouse);
  }

  @Test
  void replace_whenCreationFeasibilityFails_previousStillArchivedAndCreateNeverCalled() {
    Warehouse previousWarehouse = warehouse(BUC_TO_REPLACE, null);
    when(warehouseStore.findByBusinessUnitCode(BUC_TO_REPLACE)).thenReturn(previousWarehouse);

    Warehouse newWarehouse = warehouse(NEW_BUC, null);
    newWarehouse.location = "some raw location";
    when(validationService.validateLocation(newWarehouse.location)).thenReturn(resolvedLocation);

    RuntimeException capacityFailure = new RuntimeException("insufficient capacity");
    doThrow(capacityFailure)
        .when(validationService)
        .validateCreationFeasibilityAndCapacity(newWarehouse, resolvedLocation);

    assertThrows(RuntimeException.class, () -> useCase.replace(BUC_TO_REPLACE, newWarehouse));

    assertNotNull(previousWarehouse.archivedAt);
    verify(warehouseStore, never()).create(any());
  }
}