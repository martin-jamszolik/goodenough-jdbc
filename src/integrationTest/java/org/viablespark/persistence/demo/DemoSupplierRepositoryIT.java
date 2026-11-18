/*
 * Copyright (c) 2023 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.viablespark.persistence.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.viablespark.persistence.Key;
import org.viablespark.persistence.dsl.SqlQuery;
import org.viablespark.persistence.validation.SchemaValidator;

/**
 * Live-database integration test for the {@code demo} repositories, built by following {@code
 * docs/from-schema.md} end to end.
 *
 * <p>Runs only when a connection is configured — set {@code DEMO_JDBC_URL} (e.g. {@code
 * jdbc:mariadb://host:3306/demo}), {@code DEMO_JDBC_USER}, and {@code DEMO_JDBC_PASSWORD}.
 * Otherwise every test aborts with an assumption failure and the suite stays green. Deliberately
 * excluded from {@code check} and from the unit-test coverage gate: this proves the mapping against
 * a real engine, it does not measure it.
 *
 * <p>Write tests use unmistakable {@code GOODENOUGH-IT-} probe rows and remove them in {@code
 * finally} blocks, so repeated runs leave no residue.
 */
class DemoSupplierRepositoryIT {

  private static DataSource dataSource;
  private static SupplierRepository suppliers;
  private static SupplierCategoryRepository categories;

  @BeforeAll
  static void requireLiveDatabase() {
    String url = env("DEMO_JDBC_URL");
    String user = env("DEMO_JDBC_USER");
    String password = System.getenv("DEMO_JDBC_PASSWORD");
    Assumptions.assumeTrue(
        !url.isEmpty() && !user.isEmpty() && password != null,
        "Set DEMO_JDBC_URL, DEMO_JDBC_USER and DEMO_JDBC_PASSWORD to run live DB tests");

    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
    dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
    DemoSupplierRepositoryIT.dataSource = dataSource;
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    suppliers = new SupplierRepository(jdbc);
    categories = new SupplierCategoryRepository(jdbc);
  }

  @Test
  void mappingMatchesLiveSchema() {
    SchemaValidator.assertMappings(dataSource, Supplier.class, SupplierCategory.class);
  }

  @Test
  void readsRealCategoriesAndSuppliers() {
    List<SupplierCategory> all = categories.all();
    assertFalse(all.isEmpty(), "Expected real categories in demo.supplier_category");
    assertTrue(
        all.stream().allMatch(category -> category.getScatName() != null),
        "Every category should have a name");

    List<Supplier> firstFive = suppliers.queryEntity(new SqlQuery().limit(5), Supplier.class);
    assertFalse(firstFive.isEmpty(), "Expected real suppliers in demo.supplier");
    assertTrue(
        firstFive.stream().allMatch(supplier -> supplier.getSupName() != null),
        "Every supplier should have a name");
  }

  @Test
  void crudRoundTripWithCleanup() {
    SupplierCategory categoryProbe = new SupplierCategory();
    categoryProbe.setScatName("GOODENOUGH-IT-CAT-" + System.nanoTime());
    long scatKey = longValue(categories.save(categoryProbe).orElseThrow());

    Supplier probe = new Supplier();
    probe.setSupName("GOODENOUGH-IT-" + System.nanoTime());
    probe.setSupCity("Austin");
    probe.setSupState("TX");
    probe.setCategory(referenceTo(scatKey));

    long supKey = longValue(suppliers.save(probe).orElseThrow());
    try {
      Optional<Supplier> reloaded = suppliers.get(Key.of("sup_key", supKey), Supplier.class);
      assertTrue(reloaded.isPresent(), "Inserted supplier should be readable");
      assertEquals(probe.getSupName(), reloaded.orElseThrow().getSupName());

      Supplier toUpdate = reloaded.orElseThrow();
      toUpdate.setSupCity("Round Rock");
      suppliers.save(toUpdate);
      Optional<Supplier> updated = suppliers.get(Key.of("sup_key", supKey), Supplier.class);
      assertEquals("Round Rock", updated.orElseThrow().getSupCity());

      suppliers.delete(updated.orElseThrow());
      assertTrue(
          suppliers.get(Key.of("sup_key", supKey), Supplier.class).isEmpty(),
          "Deleted supplier should be gone");
    } finally {
      suppliers.get(Key.of("sup_key", supKey), Supplier.class).ifPresent(suppliers::delete);
      categories
          .get(Key.of("scat_key", scatKey), SupplierCategory.class)
          .ifPresent(categories::delete);
    }
  }

  private static SupplierCategory referenceTo(long scatKey) {
    SupplierCategory category = new SupplierCategory();
    category.setRefs(Key.of("scat_key", scatKey));
    return category;
  }

  private static long longValue(Key key) {
    return ((Number) key.primary().getValue()).longValue();
  }

  private static String env(String name) {
    String value = System.getenv(name);
    return value == null ? "" : value.trim();
  }
}
