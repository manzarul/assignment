package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


@QuarkusTest
class WarehouseResourceImplTest {

  @Inject WarehouseResourceImpl warehouseResource;

  @InjectMock WarehouseRepository warehouseRepository;

  @InjectMock CreateWarehouseOperation createWarehouseOperation;

  @InjectMock ArchiveWarehouseOperation archiveWarehouseOperation;

  @InjectMock ReplaceWarehouseOperation replaceWarehouseOperation;

  private Warehouse domainWarehouse(String buCode, String location, int capacity, int stock) {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = buCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    return warehouse;
  }

  private com.warehouse.api.beans.Warehouse apiWarehouse(
      String buCode, String location, int capacity, int stock) {
    var warehouse = new com.warehouse.api.beans.Warehouse();
    warehouse.setBusinessUnitCode(buCode);
    warehouse.setLocation(location);
    warehouse.setCapacity(capacity);
    warehouse.setStock(stock);
    return warehouse;
  }

  @Test
  @DisplayName("listAllWarehousesUnits() maps every domain warehouse to an API bean")
  void listAllWarehousesUnits_mapsDomainWarehousesToApiBeans() {
    when(warehouseRepository.getAll())
        .thenReturn(
            List.of(
                domainWarehouse("BU1", "loc-1", 100, 10),
                domainWarehouse("BU2", "loc-2", 200, 20)));

    List<com.warehouse.api.beans.Warehouse> result = warehouseResource.listAllWarehousesUnits();

    assertEquals(2, result.size());
    assertEquals("BU1", result.get(0).getBusinessUnitCode());
    assertEquals("loc-1", result.get(0).getLocation());
    assertEquals(100, result.get(0).getCapacity());
    assertEquals(10, result.get(0).getStock());
    assertEquals("BU2", result.get(1).getBusinessUnitCode());
  }

  @Test
  @DisplayName("createANewWarehouseUnit() maps the API bean to a domain warehouse and delegates to the create operation")
  void createANewWarehouseUnit_mapsAndDelegatesToCreateOperation() {
    var request = apiWarehouse("BU3", "loc-3", 300, 30);

    com.warehouse.api.beans.Warehouse result = warehouseResource.createANewWarehouseUnit(request);

    ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
    verify(createWarehouseOperation, times(1)).create(captor.capture());
    Warehouse created = captor.getValue();
    assertEquals("BU3", created.businessUnitCode);
    assertEquals("loc-3", created.location);
    assertEquals(300, created.capacity);
    assertEquals(30, created.stock);

    assertEquals("BU3", result.getBusinessUnitCode());
    assertEquals("loc-3", result.getLocation());
    assertEquals(300, result.getCapacity());
    assertEquals(30, result.getStock());
  }

  @Test
  @DisplayName("getAWarehouseUnitByID() returns the mapped warehouse when found")
  void getAWarehouseUnitByID_whenFound_returnsMappedWarehouse() {
    when(warehouseRepository.findByBusinessUnitCode("BU4"))
        .thenReturn(domainWarehouse("BU4", "loc-4", 400, 40));

    com.warehouse.api.beans.Warehouse result = warehouseResource.getAWarehouseUnitByID("BU4");

    assertEquals("BU4", result.getBusinessUnitCode());
    assertEquals("loc-4", result.getLocation());
    assertEquals(400, result.getCapacity());
    assertEquals(40, result.getStock());
  }

  @Test
  @DisplayName("getAWarehouseUnitByID() throws a 404 WebApplicationException when not found")
  void getAWarehouseUnitByID_whenNotFound_throws404() {
    when(warehouseRepository.findByBusinessUnitCode("MISSING")).thenReturn(null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> warehouseResource.getAWarehouseUnitByID("MISSING"));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("archiveAWarehouseUnitByID() delegates to the archive operation when found")
  void archiveAWarehouseUnitByID_whenFound_delegatesToArchiveOperation() {
    Warehouse existing = domainWarehouse("BU5", "loc-5", 500, 50);
    when(warehouseRepository.findByBusinessUnitCode("BU5")).thenReturn(existing);

    warehouseResource.archiveAWarehouseUnitByID("BU5");

    verify(archiveWarehouseOperation, times(1)).archive(existing);
  }

  @Test
  @DisplayName("archiveAWarehouseUnitByID() throws a 404 WebApplicationException when not found, without archiving")
  void archiveAWarehouseUnitByID_whenNotFound_throws404() {
    when(warehouseRepository.findByBusinessUnitCode("MISSING")).thenReturn(null);

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class,
            () -> warehouseResource.archiveAWarehouseUnitByID("MISSING"));

    assertEquals(404, ex.getResponse().getStatus());
    verify(archiveWarehouseOperation, never()).archive(any());
  }

  @Test
  @DisplayName("replaceTheCurrentActiveWarehouse() maps the request and delegates to the replace operation")
  void replaceTheCurrentActiveWarehouse_mapsAndDelegatesToReplaceOperation() {
    var request = apiWarehouse("BU6-NEW", "loc-6", 600, 60);

    com.warehouse.api.beans.Warehouse result =
        warehouseResource.replaceTheCurrentActiveWarehouse("BU6-OLD", request);

    ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
    verify(replaceWarehouseOperation, times(1)).replace(eq("BU6-OLD"), captor.capture());
    Warehouse replacement = captor.getValue();
    assertEquals("BU6-NEW", replacement.businessUnitCode);
    assertEquals("loc-6", replacement.location);
    assertEquals(600, replacement.capacity);
    assertEquals(60, replacement.stock);

    assertEquals("BU6-NEW", result.getBusinessUnitCode());
    assertEquals("loc-6", result.getLocation());
  }
}