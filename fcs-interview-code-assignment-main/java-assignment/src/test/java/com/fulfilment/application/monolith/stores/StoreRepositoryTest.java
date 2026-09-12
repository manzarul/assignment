package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StoreRepository}.
 *
 * <p>{@link Store} is a Panache active-record entity, so its static finder ({@code findById})
 * only works inside a real Panache/Hibernate runtime. {@link PanacheMock} lets us stub that
 * static call without needing a database, while still running inside a (lightweight)
 * {@code @QuarkusTest} so CDI/Arc wiring for {@link StoreRepository} itself is real.
 *
 * <p>Note: {@code PanacheMock.mock(Store.class)} only intercepts the entity's <em>static</em>
 * delegate methods, not instance methods called on a plain {@code new Store(...)} - those would
 * still hit real Hibernate. So wherever a test needs to verify or no-op an instance call like
 * {@code persist()}/{@code delete()}, we use a genuine {@code Mockito.mock(Store.class)} instead
 * of a real instance. Public fields on a Mockito mock are still ordinary field reads/writes
 * (Mockito only intercepts methods), so setting {@code mock.name = "..."} works exactly like on
 * a real instance.
 */
@QuarkusTest
class StoreRepositoryTest {

  @Inject StoreRepository storeRepository;

  @BeforeEach
  void mockPanacheEntity() {
    PanacheMock.mock(Store.class);
  }

  @AfterEach
  void resetPanacheMock() {
    PanacheMock.reset();
  }

  @Test
  @DisplayName("persist() delegates to Store.persist() and returns the same instance")
  void persist_delegatesToEntityPersistAndReturnsIt() {
    // A real `new Store(...)` would hit actual Hibernate when persist() is called; a Mockito
    // mock lets persist() no-op while still being verifiable.
    Store store = mock(Store.class);
    store.name = "north-store";

    Store result = storeRepository.persist(store);

    assertEquals(store, result);
    verify(store, times(1)).persist();
  }

  @Test
  @DisplayName("update() overwrites name and quantity on the found entity")
  void update_whenFound_overwritesFieldsAndReturnsEntity() {
    Store existing = new Store("old-name");
    existing.quantityProductsInStock = 10;
    when(Store.findById(1L)).thenReturn(existing);

    Store updatedStore = new Store("new-name");
    updatedStore.quantityProductsInStock = 99;

    Store result = storeRepository.update(1L, updatedStore);

    assertEquals("new-name", result.name);
    assertEquals(99, result.quantityProductsInStock);
    assertEquals(existing, result);
  }

  @Test
  @DisplayName("update() throws 404 WebApplicationException when the store doesn't exist")
  void update_whenNotFound_throws404() {
    when(Store.findById(eq(404L))).thenReturn(null);

    Store updatedStore = new Store("whatever");

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> storeRepository.update(404L, updatedStore));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("patch() overwrites name when entity currently has a non-null name")
  void patch_whenEntityHasExistingName_overwritesNameFromUpdatedStore() {
    Store existing = new Store("has-a-name");
    existing.quantityProductsInStock = 5;
    when(Store.findById(2L)).thenReturn(existing);

    Store updatedStore = new Store("patched-name");
    updatedStore.quantityProductsInStock = 20;

    Store result = storeRepository.patch(2L, updatedStore);

    // Note: the current implementation branches on entity.name / entity.quantityProductsInStock
    // (the EXISTING entity's state), not on updatedStore's fields - so as long as the existing
    // entity had a non-null name and non-zero quantity, both fields get overwritten.
    assertEquals("patched-name", result.name);
    assertEquals(20, result.quantityProductsInStock);
  }

  @Test
  @DisplayName("patch() skips the name update when the entity's current name is null")
  void patch_whenEntityNameIsNull_leavesNameUnchanged() {
    Store existing = new Store(null);
    existing.quantityProductsInStock = 5;
    when(Store.findById(3L)).thenReturn(existing);

    Store updatedStore = new Store("would-be-new-name");
    updatedStore.quantityProductsInStock = 20;

    Store result = storeRepository.patch(3L, updatedStore);

    assertEquals(null, result.name);
    assertEquals(20, result.quantityProductsInStock);
  }

  @Test
  @DisplayName("patch() skips the quantity update when the entity's current quantity is zero")
  void patch_whenEntityQuantityIsZero_leavesQuantityUnchanged() {
    Store existing = new Store("existing-name");
    existing.quantityProductsInStock = 0;
    when(Store.findById(4L)).thenReturn(existing);

    Store updatedStore = new Store("new-name");
    updatedStore.quantityProductsInStock = 50;

    Store result = storeRepository.patch(4L, updatedStore);

    assertEquals("new-name", result.name);
    assertEquals(0, result.quantityProductsInStock);
  }

  @Test
  @DisplayName("patch() throws 404 WebApplicationException when the store doesn't exist")
  void patch_whenNotFound_throws404() {
    when(Store.findById(eq(404L))).thenReturn(null);

    Store updatedStore = new Store("whatever");

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> storeRepository.patch(404L, updatedStore));

    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  @DisplayName("delete() deletes the entity when found")
  void delete_whenFound_deletesEntity() {
    // A real `new Store(...)` has a null id and would blow up in real Hibernate's delete() path
    // ("id to load is required for loading"); a Mockito mock no-ops delete() and is verifiable.
    Store existing = mock(Store.class);
    existing.name = "to-be-deleted";
    when(Store.findById(5L)).thenReturn(existing);

    storeRepository.delete(5L);

    verify(existing, times(1)).delete();
  }

  @Test
  @DisplayName("delete() throws 404 WebApplicationException when the store doesn't exist")
  void delete_whenNotFound_throws404() {
    when(Store.findById(eq(404L))).thenReturn(null);

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> storeRepository.delete(404L));

    assertEquals(404, ex.getResponse().getStatus());
  }
}