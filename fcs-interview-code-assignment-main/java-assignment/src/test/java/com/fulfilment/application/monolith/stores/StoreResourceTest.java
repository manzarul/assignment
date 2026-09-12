package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StoreResource}.
 *
 * <p>{@link StoreRepository} and {@link LegacyStoreManagerGateway} are replaced with Mockito
 * mocks via {@code @InjectMock} (from {@code quarkus-junit5-mockito}), so no real persistence or
 * "legacy system" side effects happen. {@code get()}/{@code getSingle()} call the {@link Store}
 * Panache entity directly, so those two tests use {@link PanacheMock} instead.
 */
@QuarkusTest
class StoreResourceTest {

  @Inject StoreResource storeResource;

  @InjectMock StoreRepository storeRepository;

  @InjectMock LegacyStoreManagerGateway legacyStoreManagerGateway;

  @Test
  @DisplayName("get() returns all stores sorted by name")
  void get_returnsAllStoresSortedByName() {
    List<PanacheEntityBase> stores = List.of(new Store("a-store"), new Store("b-store"));
    PanacheMock.mock(Store.class);
    when(Store.listAll(any())).thenReturn(stores);

    List<Store> result = storeResource.get();

    assertEquals(stores, result);
    PanacheMock.reset();
  }

  @Test
  @DisplayName("getSingle() returns the store when found")
  void getSingle_whenFound_returnsStore() {
    Store store = new Store("found-store");
    PanacheMock.mock(Store.class);
    when(Store.findById(1L)).thenReturn(store);

    Store result = storeResource.getSingle(1L);

    assertEquals(store, result);
    PanacheMock.reset();
  }

  @Test
  @DisplayName("getSingle() throws 404 when the store doesn't exist")
  void getSingle_whenNotFound_throws404() {
    PanacheMock.mock(Store.class);
    when(Store.findById(404L)).thenReturn(null);

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> storeResource.getSingle(404L));

    assertEquals(404, ex.getResponse().getStatus());
    PanacheMock.reset();
  }

  @Test
  @DisplayName("create() throws 422 when an invalid (non-positive) id is set on the request")
  void create_whenIdIsInvalid_throws422() {
    Store store = new Store("new-store");
    store.id = -1L;

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> storeResource.create(store));

    assertEquals(422, ex.getResponse().getStatus());
    verify(storeRepository, never()).persist(any());
    verify(legacyStoreManagerGateway, never()).createStoreOnLegacySystem(any());
  }

  @Test
  @DisplayName("create() persists the store and notifies the legacy gateway, returning 201")
  void create_whenValid_persistsAndNotifiesLegacyGateway() {
    Store store = new Store("new-store");
    when(storeRepository.persist(store)).thenReturn(store);

    Response response = storeResource.create(store);

    assertEquals(201, response.getStatus());
    assertEquals(store, response.getEntity());
    verify(storeRepository, times(1)).persist(store);
    verify(legacyStoreManagerGateway, times(1)).createStoreOnLegacySystem(store);
  }

  @Test
  @DisplayName("update() throws 422 when the name is null, blank, or under 5 characters")
  void update_whenNameInvalid_throws422() {
    Store nullName = new Store(null);
    Store blankName = new Store("   ");
    Store shortName = new Store("abcd");

    for (Store invalid : List.of(nullName, blankName, shortName)) {
      WebApplicationException ex =
          assertThrows(WebApplicationException.class, () -> storeResource.update(1L, invalid));
      assertEquals(422, ex.getResponse().getStatus());
    }
    verify(storeRepository, never()).update(any(), any());
    verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
  }

  @Test
  @DisplayName("update() updates the store and notifies the legacy gateway")
  void update_whenValid_updatesAndNotifiesLegacyGateway() {
    Store updatedStore = new Store("valid-name");
    Store persistedEntity = new Store("valid-name");
    when(storeRepository.update(1L, updatedStore)).thenReturn(persistedEntity);

    Store result = storeResource.update(1L, updatedStore);

    assertEquals(persistedEntity, result);
    verify(storeRepository, times(1)).update(1L, updatedStore);
    verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(persistedEntity);
  }

  @Test
  @DisplayName("update() propagates the 404 from the repository when the store doesn't exist")
  void update_whenRepositoryThrows404_propagatesAndSkipsLegacyNotification() {
    Store updatedStore = new Store("valid-name");
    when(storeRepository.update(eq(404L), eq(updatedStore)))
        .thenThrow(new WebApplicationException("not found", 404));

    WebApplicationException ex =
        assertThrows(
            WebApplicationException.class, () -> storeResource.update(404L, updatedStore));

    assertEquals(404, ex.getResponse().getStatus());
    verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
  }

  @Test
  @DisplayName("patch() throws 422 when the name is null")
  void patch_whenNameIsNull_throws422() {
    Store updatedStore = new Store(null);

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> storeResource.patch(1L, updatedStore));

    assertEquals(422, ex.getResponse().getStatus());
    verify(storeRepository, never()).patch(any(), any());
    verify(legacyStoreManagerGateway, never()).updateStoreOnLegacySystem(any());
  }

  @Test
  @DisplayName("patch() updates the store and notifies the legacy gateway")
  void patch_whenValid_notifiesLegacyGateway() {
    Store updatedStore = new Store("patched-name");
    Store persistedEntity = new Store("patched-name");
    when(storeRepository.patch(1L, updatedStore)).thenReturn(persistedEntity);

    Store result = storeResource.patch(1L, updatedStore);

    assertEquals(persistedEntity, result);
    verify(legacyStoreManagerGateway, times(1)).updateStoreOnLegacySystem(persistedEntity);
  }

  @Test
  @DisplayName("delete() delegates to the repository and returns 204")
  void delete_delegatesToRepositoryAndReturns204() {
    Response response = storeResource.delete(7L);

    assertEquals(204, response.getStatus());
    verify(storeRepository, times(1)).delete(7L);
  }

  @Test
  @DisplayName("delete() propagates the repository's 404 when the store doesn't exist")
  void delete_whenRepositoryThrows404_propagates() {
    org.mockito.Mockito.doThrow(new WebApplicationException("not found", 404))
        .when(storeRepository)
        .delete(404L);

    WebApplicationException ex =
        assertThrows(WebApplicationException.class, () -> storeResource.delete(404L));

    assertEquals(404, ex.getResponse().getStatus());
  }

  /**
   * {@link StoreResource.ErrorMapper} is tested as a plain object (not resolved via JAX-RS/CDI)
   * since its only collaborator is an {@link ObjectMapper}, which we inject via reflection to
   * avoid depending on RESTEasy's provider-discovery machinery in a unit test.
   */
  @Nested
  class ErrorMapperTest {

    private final StoreResource.ErrorMapper errorMapper = new StoreResource.ErrorMapper();

    @BeforeEach
    void injectObjectMapper() throws NoSuchFieldException, IllegalAccessException {
      Field field = StoreResource.ErrorMapper.class.getDeclaredField("objectMapper");
      field.setAccessible(true);
      field.set(errorMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("maps a WebApplicationException using its own status code")
    void toResponse_withWebApplicationException_usesItsStatusCode() {
      WebApplicationException exception = new WebApplicationException("Store with id of 9 does not exist.", 404);

      Response response = errorMapper.toResponse(exception);

      assertEquals(404, response.getStatus());
    }

    @Test
    @DisplayName("maps any other exception to a 500")
    void toResponse_withGenericException_defaultsTo500() {
      RuntimeException exception = new RuntimeException("boom");

      Response response = errorMapper.toResponse(exception);

      assertEquals(500, response.getStatus());
    }
  }
}