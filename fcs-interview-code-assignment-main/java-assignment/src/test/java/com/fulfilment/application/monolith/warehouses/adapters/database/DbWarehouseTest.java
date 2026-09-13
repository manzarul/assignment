package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


class DbWarehouseTest {

  @Test
  @DisplayName("toWarehouse() copies every field across, including a null archivedAt")
  void toWarehouse_mapsAllFieldsIncludingNullArchivedAt() {
    DbWarehouse entity = new DbWarehouse();
    entity.id = 1L;
    entity.businessUnitCode = "BU1";
    entity.location = "warehouse-a";
    entity.capacity = 500;
    entity.stock = 42;
    entity.createdAt = LocalDateTime.of(2024, 3, 15, 9, 30);
    entity.archivedAt = null;

    Warehouse result = entity.toWarehouse();

    assertEquals("BU1", result.businessUnitCode);
    assertEquals("warehouse-a", result.location);
    assertEquals(500, result.capacity);
    assertEquals(42, result.stock);
    assertEquals(LocalDateTime.of(2024, 3, 15, 9, 30), result.createdAt);
    assertNull(result.archivedAt);
  }

  @Test
  @DisplayName("toWarehouse() carries a non-null archivedAt across too")
  void toWarehouse_mapsNonNullArchivedAt() {
    DbWarehouse entity = new DbWarehouse();
    entity.businessUnitCode = "BU2";
    entity.location = "warehouse-b";
    entity.capacity = 10;
    entity.stock = 0;
    entity.createdAt = LocalDateTime.of(2023, 1, 1, 0, 0);
    entity.archivedAt = LocalDateTime.of(2024, 12, 31, 23, 59);

    Warehouse result = entity.toWarehouse();

    assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59), result.archivedAt);
  }
}