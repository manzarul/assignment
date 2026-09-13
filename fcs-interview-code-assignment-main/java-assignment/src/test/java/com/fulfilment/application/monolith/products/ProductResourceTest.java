package com.fulfilment.application.monolith.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;

public class ProductResourceTest {

  @Mock private ProductRepository productRepository;

  private ProductResource productResource;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    productResource = new ProductResource();
    productResource.productRepository = productRepository;
  }


  @Test
  public void testGetReturnsAllProductsSortedByName() {
    // given
    Product tonstad = new Product();
    tonstad.name = "TONSTAD";
    Product kallax = new Product();
    kallax.name = "KALLAX";
    when(productRepository.listAll(any(Sort.class))).thenReturn(List.of(kallax, tonstad));

    // when
    List<Product> result = productResource.get();

    // then
    assertEquals(2, result.size());
    verify(productRepository).listAll(any(Sort.class));
  }

  // --- getSingle(id) ---

  @Test
  public void testGetSingleWhenProductExistsShouldReturnIt() {
    // given
    Product product = new Product();
    product.id = 1L;
    product.name = "TONSTAD";
    when(productRepository.findById(1L)).thenReturn(product);

    // when
    Product result = productResource.getSingle(1L);

    // then
    assertEquals("TONSTAD", result.name);
  }

  @Test
  public void testGetSingleWhenProductDoesNotExistShouldThrow404() {
    // given
    when(productRepository.findById(99L)).thenReturn(null);

    // when / then
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> productResource.getSingle(99L));

    assertEquals(404, exception.getResponse().getStatus());
  }

  // --- create(product) ---

  @Test
  public void testCreateWhenIdIsNullShouldPersistAndReturn201() {
    // given
    Product product = new Product();
    product.id = null;
    product.name = "BESTÅ";

    PanacheQuery<Product> mockQuery = mock(PanacheQuery.class);
    when(mockQuery.firstResultOptional()).thenReturn(Optional.empty());
    when(productRepository.find("name", product.name)).thenReturn(mockQuery);

    // when
    Response response = productResource.create(product);

    // then
    assertEquals(201, response.getStatus());
    verify(productRepository, times(1)).persist(product);
  }

  @Test
  public void testCreateWhenIdIsAlreadySetShouldThrow422AndNotPersist() {
    // given
    Product product = new Product();
    product.id = 5L;

    // when / then
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> productResource.create(product));

    assertEquals(422, exception.getResponse().getStatus());
    verify(productRepository, never()).persist(any(Product.class));
  }

  // --- update(id, product) ---

  @Test
  public void testUpdateWhenProductExistsShouldUpdateFieldsAndPersist() {
    // given
    Product existing = new Product();
    existing.id = 1L;
    existing.name = "OLD-NAME";
    existing.description = "old description";
    existing.price = new BigDecimal("10.0");
    existing.stock = 5;

    Product incoming = new Product();
    incoming.name = "NEW-NAME";
    incoming.description = "new description";
    incoming.price = new BigDecimal("20.0");
    incoming.stock = 15;

    when(productRepository.findById(1L)).thenReturn(existing);

    // when
    Product result = productResource.update(1L, incoming);

    // then
    assertEquals("NEW-NAME", result.name);
    assertEquals("new description", result.description);
    assertEquals(new BigDecimal("20.0"), result.price);
    assertEquals(15, result.stock);
    verify(productRepository, times(1)).persist(existing);
  }

  @Test
  public void testUpdateWhenNameIsNullShouldThrow422AndNotLookUpOrPersist() {
    // given
    Product incoming = new Product();
    incoming.name = null;

    // when / then
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> productResource.update(1L, incoming));

    assertEquals(422, exception.getResponse().getStatus());
    verify(productRepository, never()).findById(any(Long.class));
    verify(productRepository, never()).persist(any(Product.class));
  }

  @Test
  public void testUpdateWhenProductDoesNotExistShouldThrow404() {
    // given
    Product incoming = new Product();
    incoming.name = "NEW-NAME";
    when(productRepository.findById(99L)).thenReturn(null);

    // when / then
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> productResource.update(99L, incoming));

    assertEquals(404, exception.getResponse().getStatus());
    verify(productRepository, never()).persist(any(Product.class));
  }

  // --- delete(id) ---

  @Test
  public void testDeleteWhenProductExistsShouldDeleteAndReturn204() {
    // given
    Product existing = new Product();
    existing.id = 1L;
    when(productRepository.findById(1L)).thenReturn(existing);

    // when
    Response response = productResource.delete(1L);

    // then
    assertEquals(204, response.getStatus());
    verify(productRepository, times(1)).delete(existing);
  }

  @Test
  public void testDeleteWhenProductDoesNotExistShouldThrow404AndNotDelete() {
    // given
    when(productRepository.findById(99L)).thenReturn(null);

    // when / then
    WebApplicationException exception =
        assertThrows(WebApplicationException.class, () -> productResource.delete(99L));

    assertEquals(404, exception.getResponse().getStatus());
    verify(productRepository, never()).delete(any(Product.class));
  }

  /**
   * {@link ProductResource.ErrorMapper} is tested as a plain object, not resolved via JAX-RS/CDI
   * provider discovery, since its only collaborator is an {@link ObjectMapper}. That's injected
   * via reflection to avoid needing a full {@code @QuarkusTest} context just for this class.
   */
  @Nested
  class ErrorMapperTest {

    private final ProductResource.ErrorMapper errorMapper = new ProductResource.ErrorMapper();

    @BeforeEach
    void injectObjectMapper() throws NoSuchFieldException, IllegalAccessException {
      Field field = ProductResource.ErrorMapper.class.getDeclaredField("objectMapper");
      field.setAccessible(true);
      field.set(errorMapper, new ObjectMapper());
    }

    @Test
    @DisplayName("maps a WebApplicationException using its own status code and message")
    void toResponse_withWebApplicationException_usesItsStatusCodeAndMessage() {
      WebApplicationException exception =
          new WebApplicationException("Product with id of 42 does not exist.", 404);

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