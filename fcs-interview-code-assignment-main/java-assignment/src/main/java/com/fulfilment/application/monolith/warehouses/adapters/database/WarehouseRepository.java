package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public List<Warehouse> getAll() {
    // Archived warehouses are a soft-deleted state (archivedAt set) and should not appear in
    // the default listing - only currently-active warehouses do.
    return this.list("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    DbWarehouse entity = new DbWarehouse();
    entity.businessUnitCode = warehouse.businessUnitCode;
    entity.location = warehouse.location;
    entity.capacity = warehouse.capacity;
    entity.stock = warehouse.stock;
    entity.createdAt = warehouse.createdAt;
    entity.archivedAt = warehouse.archivedAt;
    persist(entity);
  }

  @Override
  @Transactional
  public void update(Warehouse warehouse) {
    DbWarehouse entity = find("businessUnitCode", warehouse.businessUnitCode).firstResult();
    if (entity == null) {
      throw new WebApplicationException(
          "Warehouse with business unit code '" + warehouse.businessUnitCode + "' does not exist.",
          404);
    }
    entity.location = warehouse.location;
    entity.capacity = warehouse.capacity;
    entity.stock = warehouse.stock;
    entity.archivedAt = warehouse.archivedAt;
    // no explicit persist() needed: entity is managed within this transaction, so Hibernate
    // flushes the changes on commit.
  }

  @Override
  @Transactional
  public void remove(Warehouse warehouse) {
    delete("businessUnitCode", warehouse.businessUnitCode);
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse entity = find("businessUnitCode", buCode).firstResult();
    return entity == null ? null : entity.toWarehouse();
  }
}