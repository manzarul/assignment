package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  private LocationGateway locationGateway;

  @BeforeEach
  public void setUp() {
    locationGateway = new LocationGateway();
  }

  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    // given
    String identifier = "ZWOLLE-001";

    // when
    Location location = locationGateway.resolveByIdentifier(identifier);

    // then
    assertNotNull(location);
    assertEquals("ZWOLLE-001", location.getIdentification());
    assertEquals(1, location.getMaxNumberOfWarehouses());
    assertEquals(40, location.getMaxCapacity());
  }

  @Test
  public void testWhenResolveExistingLocationWithDifferentCaseShouldReturn() {
    // given: identifier casing differs from how it's stored
    String identifier = "zwolle-001";

    // when
    Location location = locationGateway.resolveByIdentifier(identifier);

    // then: lookup is case-insensitive, so it should still resolve
    assertNotNull(location);
    assertEquals("ZWOLLE-001", location.getIdentification());
  }

  @Test
  public void testWhenResolveAnotherExistingLocationShouldReturnCorrectValues() {
    // given
    String identifier = "AMSTERDAM-002";

    // when
    Location location = locationGateway.resolveByIdentifier(identifier);

    // then
    assertNotNull(location);
    assertEquals("AMSTERDAM-002", location.getIdentification());
    assertEquals(3, location.getMaxNumberOfWarehouses());
    assertEquals(75, location.getMaxCapacity());
  }

  @Test
  public void testWhenResolveNonExistingLocationShouldThrow() {
    // given
    String identifier = "NON-EXISTENT-LOCATION";

    // when / then
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> locationGateway.resolveByIdentifier(identifier));

    assertEquals("Location not found for identifier: " + identifier, exception.getMessage());
  }

  @Test
  public void testWhenResolveWithNullIdentifierShouldThrow() {
    // given
    String identifier = null;

    // when / then: equalsIgnoreCase is called on the stored identification against a null
    // argument, which returns false for every entry rather than NPEing, so this should still
    // surface as the same "not found" exception rather than a NullPointerException.
    assertThrows(
        IllegalArgumentException.class, () -> locationGateway.resolveByIdentifier(identifier));
  }
}