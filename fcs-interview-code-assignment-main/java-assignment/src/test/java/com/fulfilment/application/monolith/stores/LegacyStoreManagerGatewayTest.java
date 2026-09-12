package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;


class LegacyStoreManagerGatewayTest {

  private final LegacyStoreManagerGateway gateway = new LegacyStoreManagerGateway();

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void redirectStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private Store newStore(String name, int quantity) {
    Store store = new Store();
    store.name = name;
    store.quantityProductsInStock = quantity;
    return store;
  }

  /** Pulls the temp file path back out of the "Temporary file created at: ..." log line. */
  private Path extractTempFilePath(String stdout) {
    Matcher matcher = Pattern.compile("Temporary file created at: (.+)").matcher(stdout);
    assertTrue(matcher.find(), "Expected stdout to contain the temp file creation log line");
    return Path.of(matcher.group(1).trim());
  }

  @Test
  @DisplayName("createStoreOnLegacySystem writes, verifies, then deletes the temp file")
  void createStoreOnLegacySystem_happyPath_writesAndCleansUpTempFile() {
    Store store = newStore("north-store", 42);

    gateway.createStoreOnLegacySystem(store);

    String stdout = outContent.toString();
    Path tempFile = extractTempFilePath(stdout);

    assertTrue(stdout.contains("Data written to temporary file."));
    assertTrue(
        stdout.contains(
            "Data read from temporary file: Store created. [ name =north-store ] [ items on stock =42]"));
    assertTrue(stdout.contains("Temporary file deleted."));

    // Step 4 in the gateway deletes the file, so by the time we get here it must be gone.
    assertFalse(Files.exists(tempFile), "Temp file should have been deleted by the gateway");
    assertTrue(errContent.toString().isEmpty(), "No exception should have been logged");
  }

  @Test
  @DisplayName("updateStoreOnLegacySystem writes, verifies, then deletes the temp file")
  void updateStoreOnLegacySystem_happyPath_writesAndCleansUpTempFile() {
    Store store = newStore("south-store", 7);

    gateway.updateStoreOnLegacySystem(store);

    String stdout = outContent.toString();
    Path tempFile = extractTempFilePath(stdout);

    assertTrue(stdout.contains("Data written to temporary file."));
    assertTrue(stdout.contains("Temporary file deleted."));
    assertFalse(Files.exists(tempFile));
    assertTrue(errContent.toString().isEmpty());
  }

  @Test
  @DisplayName("createStoreOnLegacySystem swallows exceptions and logs them instead of throwing")
  void createStoreOnLegacySystem_whenFileCreationFails_exceptionIsCaughtAndLogged() {
    Store store = newStore("failing-store", 1);

    try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createTempFile(store.name, ".txt"))
          .thenThrow(new IOException("disk full"));

      // Should NOT throw - the gateway catches everything internally.
      gateway.createStoreOnLegacySystem(store);
    }

    assertTrue(
        errContent.toString().contains("java.io.IOException: disk full"),
        "Expected the caught exception's stack trace to be printed to stderr");
    assertFalse(outContent.toString().contains("Data written to temporary file."));
  }

  @Test
  @DisplayName("updateStoreOnLegacySystem swallows exceptions and logs them instead of throwing")
  void updateStoreOnLegacySystem_whenWriteFails_exceptionIsCaughtAndLogged() throws Exception {
    Store store = newStore("write-failure-store", 3);
    Path realTempFile = Files.createTempFile("placeholder", ".txt");

    try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createTempFile(store.name, ".txt"))
          .thenReturn(realTempFile);
      filesMock
          .when(() -> Files.write(eq(realTempFile), any(byte[].class)))
          .thenThrow(new IOException("write failed"));

      gateway.updateStoreOnLegacySystem(store);
    } finally {
      Files.deleteIfExists(realTempFile);
    }

    assertTrue(errContent.toString().contains("java.io.IOException: write failed"));
    assertFalse(outContent.toString().contains("Data written to temporary file."));
  }
}