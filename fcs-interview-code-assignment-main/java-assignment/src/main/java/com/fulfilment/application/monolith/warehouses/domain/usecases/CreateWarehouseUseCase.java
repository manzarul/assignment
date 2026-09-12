package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseValidationService validationService;

  @Inject
  public CreateWarehouseUseCase(
      WarehouseStore warehouseStore, WarehouseValidationService validationService) {
    this.warehouseStore = warehouseStore;
    this.validationService = validationService;
  }

  @Override
  public void create(Warehouse warehouse) {

    validationService.validateBusinessUnitCodeIsFree(warehouse.businessUnitCode);

    Location location = validationService.validateLocation(warehouse.location);

    validationService.validateCreationFeasibilityAndCapacity(warehouse, location);

    warehouse.createdAt = LocalDateTime.now();
    warehouse.archivedAt = null;

    warehouseStore.create(warehouse);
  }
}