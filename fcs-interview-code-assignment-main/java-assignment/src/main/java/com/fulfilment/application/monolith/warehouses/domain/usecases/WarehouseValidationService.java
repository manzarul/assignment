package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

/**
 * Shared validation rules for warehouse creation and replacement, so both use cases enforce the
 * same business constraints instead of duplicating them.
 */
@ApplicationScoped
public class WarehouseValidationService {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  @Inject
  public WarehouseValidationService(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  /** Business Unit Code Verification: the code must not belong to another active warehouse. */
  public void validateBusinessUnitCodeIsFree(String businessUnitCode) {
    if (businessUnitCode == null || businessUnitCode.isBlank()) {
      throw new WebApplicationException("Warehouse business unit code must be provided.", 422);
    }

    Warehouse existing = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    if (existing != null && existing.archivedAt == null) {
      throw new WebApplicationException(
          "A warehouse with business unit code '" + businessUnitCode + "' already exists.", 409);
    }
  }

  /** Location Validation: the location must be a known, existing location. */
  public Location validateLocation(String locationIdentifier) {
    if (locationIdentifier == null || locationIdentifier.isBlank()) {
      throw new WebApplicationException("Warehouse location must be provided.", 422);
    }

    Location location = locationResolver.resolveByIdentifier(locationIdentifier);
    if (location == null) {
      throw new WebApplicationException(
          "Location '" + locationIdentifier + "' is not a valid location.", 422);
    }
    return location;
  }

  /**
   * Warehouse Creation Feasibility + Capacity and Stock Validation: checks that another
   * warehouse can still be created at this location, and that the candidate's capacity fits
   * within what's left of the location's overall capacity budget and can hold its own declared
   * stock.
   */
  public void validateCreationFeasibilityAndCapacity(Warehouse candidate, Location location) {

    long activeWarehousesAtLocation = countActiveWarehousesAtLocation(location.identification);

    if (activeWarehousesAtLocation >= location.maxNumberOfWarehouses) {
      throw new WebApplicationException(
          "The maximum number of warehouses ("
              + location.maxNumberOfWarehouses
              + ") for location '"
              + location.identification
              + "' has already been reached.",
          422);
    }

    if (candidate.capacity == null || candidate.capacity <= 0) {
      throw new WebApplicationException("Warehouse capacity must be a positive number.", 422);
    }

    if (candidate.stock == null || candidate.stock < 0) {
      throw new WebApplicationException("Warehouse stock must not be negative.", 422);
    }

    if (candidate.stock > candidate.capacity) {
      throw new WebApplicationException(
          "Warehouse capacity ("
              + candidate.capacity
              + ") cannot hold the informed stock ("
              + candidate.stock
              + ").",
          422);
    }

    int capacityAlreadyUsedAtLocation = sumActiveCapacityAtLocation(location.identification);

    if (capacityAlreadyUsedAtLocation + candidate.capacity > location.maxCapacity) {
      throw new WebApplicationException(
          "Warehouse capacity ("
              + candidate.capacity
              + ") would exceed the maximum capacity ("
              + location.maxCapacity
              + ") allowed for location '"
              + location.identification
              + "'.",
          422);
    }
  }

  /**
   * Additional validations for replacing a warehouse: the new warehouse must be able to
   * accommodate the stock carried over from the warehouse it's replacing, and its own declared
   * stock must match the previous warehouse's stock exactly.
   */
  public void validateReplacementRules(Warehouse previousWarehouse, Warehouse newWarehouse) {

    if (newWarehouse.capacity == null || newWarehouse.capacity < previousWarehouse.stock) {
      throw new WebApplicationException(
          "The new warehouse's capacity ("
              + newWarehouse.capacity
              + ") cannot accommodate the stock ("
              + previousWarehouse.stock
              + ") from the warehouse being replaced.",
          422);
    }

    if (newWarehouse.stock == null || !newWarehouse.stock.equals(previousWarehouse.stock)) {
      throw new WebApplicationException(
          "The new warehouse's stock ("
              + newWarehouse.stock
              + ") must match the stock of the warehouse being replaced ("
              + previousWarehouse.stock
              + ").",
          422);
    }
  }

  private long countActiveWarehousesAtLocation(String locationIdentifier) {
    return warehouseStore.getAll().stream()
        .filter(w -> locationIdentifier.equals(w.location) && w.archivedAt == null)
        .count();
  }

  private int sumActiveCapacityAtLocation(String locationIdentifier) {
    return warehouseStore.getAll().stream()
        .filter(w -> locationIdentifier.equals(w.location) && w.archivedAt == null)
        .mapToInt(w -> w.capacity == null ? 0 : w.capacity)
        .sum();
  }
}