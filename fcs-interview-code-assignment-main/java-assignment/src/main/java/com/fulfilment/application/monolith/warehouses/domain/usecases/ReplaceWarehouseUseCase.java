package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseValidationService validationService;

  @Inject
  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore, WarehouseValidationService validationService) {
    this.warehouseStore = warehouseStore;
    this.validationService = validationService;
  }

  @Override
  public void replace(String businessUnitCodeToReplace, Warehouse newWarehouse) {

    Warehouse previousWarehouse = warehouseStore.findByBusinessUnitCode(businessUnitCodeToReplace);
    if (previousWarehouse == null || previousWarehouse.archivedAt != null) {
      throw new WebApplicationException(
          "There is no active warehouse with business unit code '"
              + businessUnitCodeToReplace
              + "' to replace.",
          404);
    }

    // Additional rules specific to replacement: capacity accommodation + stock matching.
    validationService.validateReplacementRules(previousWarehouse, newWarehouse);

    // Archive the outgoing warehouse first so its location slot and capacity are freed up
    // before we check whether the incoming warehouse can be created there.
    previousWarehouse.archivedAt = LocalDateTime.now();
    warehouseStore.update(previousWarehouse);

    // The incoming warehouse must satisfy the same rules a brand-new warehouse would.
    validationService.validateBusinessUnitCodeIsFree(newWarehouse.businessUnitCode);
    Location location = validationService.validateLocation(newWarehouse.location);
    validationService.validateCreationFeasibilityAndCapacity(newWarehouse, location);

    newWarehouse.createdAt = LocalDateTime.now();
    newWarehouse.archivedAt = null;

    warehouseStore.create(newWarehouse);
  }
}