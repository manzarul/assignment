package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WarehouseRepository}.
 *
 * <p>{@code WarehouseRepository} implements {@code PanacheRepository<DbWarehouse>} (the
 * repository pattern), not active-record entities. {@link io.quarkus.panache.mock.PanacheMock}
 * only supports active-record ({@code PanacheEntityBase}) classes - trying to mock a
 * {@code PanacheRepositoryBase} with it is a silent no-op (the real Hibernate calls still run
 * underneath), so these tests instead run for real against the test datasource inside
 * {@code @TestTransaction}, which automatically rolls back everything after each test method -
 * no manual cleanup needed and no cross-test pollution.
 */
@QuarkusTest
class WarehouseRepositoryTest {

  @Inject WarehouseRepository warehouseRepository;

  private Warehouse newWarehouse(String buCode, String location, int capacity, int stock) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = buCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    warehouse.createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
    return warehouse;
  }

  @Test
  @TestTransaction
  @DisplayName("create() persists a new warehouse that can then be found by business unit code")
  void create_persistsWarehouseFindableAfterwards() {
    Warehouse warehouse = newWarehouse("BU-CREATE-1", "loc-1", 300, 30);

    warehouseRepository.create(warehouse);

    Warehouse found = warehouseRepository.findByBusinessUnitCode("BU-CREATE-1");
    assertEquals("BU-CREATE-1", found.businessUnitCode);
    assertEquals("loc-1", found.location);
    assertEquals(300, found.capacity);
    assertEquals(30, found.stock);
    assertEquals(warehouse.createdAt, found.createdAt);
    assertNull(found.archivedAt);
  }

  @Test
  @TestTransaction
  @DisplayName("update() overwrites location, capacity, stock, and archivedAt on an existing warehouse")
  void update_whenFound_overwritesFields() {
    warehouseRepository.create(newWarehouse("BU-UPDATE-1", "old-loc", 100, 10));

    Warehouse updated = newWarehouse("BU-UPDATE-1", "new-loc", 999, 99);
    updated.archivedAt = LocalDateTime.of(2024, 6, 1, 0, 0);

    warehouseRepository.update(updated);

    Warehouse found = warehouseRepository.findByBusinessUnitCode("BU-UPDATE-1");
    assertEquals("new-loc", found.location);
    assertEquals(999, found.capacity);
    assertEquals(99, found.stock);
    assertEquals(updated.archivedAt, found.archivedAt);
  }

  @Test
  @TestTransaction
  @DisplayName("update() throws a 404 WebApplicationException when the business unit code doesn't exist")
  void update_whenNotFound_throws404() {
    Warehouse updated = newWarehouse("BU-DOES-NOT-EXIST", "loc", 1, 1);

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> warehouseRepository.update(updated));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  @TestTransaction
  @DisplayName("remove() deletes the warehouse so it can no longer be found")
  void remove_deletesWarehouse() {
    warehouseRepository.create(newWarehouse("BU-REMOVE-1", "loc", 1, 1));
    Warehouse toRemove = warehouseRepository.findByBusinessUnitCode("BU-REMOVE-1");

    warehouseRepository.remove(toRemove);

    assertNull(warehouseRepository.findByBusinessUnitCode("BU-REMOVE-1"));
  }

  @Test
  @TestTransaction
  @DisplayName("getAll() includes every persisted, non-archived warehouse")
  void getAll_includesPersistedWarehouses() {
    warehouseRepository.create(newWarehouse("BU-ALL-1", "loc-1", 10, 1));
    warehouseRepository.create(newWarehouse("BU-ALL-2", "loc-2", 20, 2));

    List<Warehouse> all = warehouseRepository.getAll();

    assertTrue(all.stream().anyMatch(w -> "BU-ALL-1".equals(w.businessUnitCode)));
    assertTrue(all.stream().anyMatch(w -> "BU-ALL-2".equals(w.businessUnitCode)));
  }

  @Test
  @TestTransaction
  @DisplayName("getAll() excludes warehouses that have been archived")
  void getAll_excludesArchivedWarehouses() {
    warehouseRepository.create(newWarehouse("BU-ARCHIVED-1", "loc-1", 10, 1));
    Warehouse toArchive = warehouseRepository.findByBusinessUnitCode("BU-ARCHIVED-1");
    toArchive.archivedAt = LocalDateTime.of(2024, 1, 1, 0, 0);
    warehouseRepository.update(toArchive);

    List<Warehouse> all = warehouseRepository.getAll();

    assertTrue(all.stream().noneMatch(w -> "BU-ARCHIVED-1".equals(w.businessUnitCode)));
    // findByBusinessUnitCode() is a separate lookup path and is intentionally unaffected -
    // e.g. getAWarehouseUnitByID() still needs to find archived warehouses by id.
    assertEquals("BU-ARCHIVED-1", warehouseRepository.findByBusinessUnitCode("BU-ARCHIVED-1").businessUnitCode);
  }

  @Test
  @TestTransaction
  @DisplayName("findByBusinessUnitCode() returns null when no warehouse matches")
  void findByBusinessUnitCode_whenNotFound_returnsNull() {
    Warehouse result = warehouseRepository.findByBusinessUnitCode("BU-DOES-NOT-EXIST");

    assertNull(result);
  }
}