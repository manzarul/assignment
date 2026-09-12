package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ArchiveWarehouseUseCaseTest {

  @Mock private WarehouseStore warehouseStore;

  private ArchiveWarehouseUseCase archiveWarehouseUseCase;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    archiveWarehouseUseCase = new ArchiveWarehouseUseCase(warehouseStore);
  }

  @Test
  public void testArchiveWhenWarehouseIsNotYetArchivedShouldSetArchivedAtAndUpdate() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.001";
    warehouse.archivedAt = null;

    // when
    archiveWarehouseUseCase.archive(warehouse);

    // then
    assertNotNull(warehouse.archivedAt);

    ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
    verify(warehouseStore, times(1)).update(captor.capture());
    assertEquals("MWH.001", captor.getValue().businessUnitCode);
    assertNotNull(captor.getValue().archivedAt);
  }

  @Test
  public void testArchiveWhenWarehouseIsAlreadyArchivedShouldNotOverwriteArchivedAtButStillUpdate() {
    // given
    LocalDateTime originalArchivedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.001";
    warehouse.archivedAt = originalArchivedAt;

    // when
    archiveWarehouseUseCase.archive(warehouse);

    // then: archivedAt should be left untouched, not overwritten with a new timestamp
    assertEquals(originalArchivedAt, warehouse.archivedAt);
    verify(warehouseStore, times(1)).update(warehouse);
  }

  @Test
  public void testArchiveShouldAlwaysDelegateToWarehouseStoreUpdate() {
    // given
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.012";

    // when
    archiveWarehouseUseCase.archive(warehouse);

    // then
    verify(warehouseStore, times(1)).update(warehouse);
  }
}