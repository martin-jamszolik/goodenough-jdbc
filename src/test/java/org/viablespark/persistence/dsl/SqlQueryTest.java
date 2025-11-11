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

package org.viablespark.persistence.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/** */
public class SqlQueryTest {

  public SqlQueryTest() {}

  @Test
  public void testClause() {
    SqlQuery q = new SqlQuery().clause("WHERE pri_key=? AND sec_date >=?", 233L, new Date());

    String result = q.sql();
    assertEquals("WHERE pri_key=? AND sec_date >=?", result);
  }

  @Test
  public void testCondition() {
    SqlQuery q =
        new SqlQuery()
            .where("pri_key=?", 233L)
            .andWhere("sec_date >=?", new Date())
            .orWhere("(other_key =?", 23L)
            .andWhere("something !=?)", "elseHere");

    String result = q.sql();
    assertEquals("WHERE pri_key=? AND sec_date >=? OR (other_key =? AND something !=?)", result);
  }

  @Test
  public void testOrderBy() {
    SqlQuery q = new SqlQuery().where("pri_key=?", 233L).andWhere("sec_date >=?", new Date());
    String result = q.orderBy("testColumn", SqlQuery.Direction.DESC).sql();
    assertEquals("WHERE pri_key=? AND sec_date >=? ORDER BY testColumn desc", result);
  }

  @Test
  public void testLimit() {
    SqlQuery q = new SqlQuery().where("pri_key=?", 233L).andWhere("sec_date >=?", new Date());
    String result = q.limit(5).sql();
    assertEquals("WHERE pri_key=? AND sec_date >=? LIMIT 5", result);
  }

  @Test
  public void testDynamicQuery() {
    String name = "123 main";

    var query =
        new SqlQuery()
            .select("SELECT COUNT(*)")
            .from("1_proposalproject p")
            .join("INNER JOIN company c using(c_key)")
            .join("INNER JOIN 1_estlocation el using(el_key)")
            .join("INNER JOIN estimator e using(e_key)")
            .where("pp_key IS NOT NULL")
            .andWhere("pp_name like ?", "%" + name + "%");

    assertTrue(query.sql().contains("SELECT COUNT(*) FROM 1_proposalproject p"));
    assertEquals(1, query.values().length);
    assertTrue(
        query
            .sql()
            .contains(
                "INNER JOIN estimator e using(e_key) WHERE pp_key IS NOT NULL AND pp_name like ?"));

    query.select("SELECT p.pp_key, pp_name, tax, pp_status, pp_date, pp_owner");
    query.paginate(20, 0);

    assertTrue(
        query
            .sql()
            .contains(
                "SELECT p.pp_key, pp_name, tax, pp_status, pp_date, pp_owner FROM 1_proposalproject p"));
    assertEquals(1, query.values().length);
  }

  @Test
  public void testOffset() {
    SqlQuery q = new SqlQuery().where("id=?", 1).offset(10);
    assertEquals("WHERE id=? OFFSET 10", q.sql());
  }

  @Test
  public void testLimitAndOffset() {
    SqlQuery q = new SqlQuery().where("id=?", 1).limit(5).offset(10);
    assertTrue(q.sql().contains("LIMIT 5"));
    assertTrue(q.sql().contains("OFFSET 10"));
  }

  @Test
  public void testMultipleOrderBy() {
    SqlQuery q =
        new SqlQuery()
            .where("status=?", "active")
            .orderBy("created_date", SqlQuery.Direction.DESC)
            .orderBy("name", SqlQuery.Direction.ASC);
    assertTrue(q.sql().contains("ORDER BY created_date desc, name asc"));
  }

  @Test
  public void testOrderByRawExpression() {
    SqlQuery q = new SqlQuery().where("id>?", 0).orderBy("CASE WHEN priority=1 THEN 0 ELSE 1 END");
    assertTrue(q.sql().contains("ORDER BY CASE WHEN priority=1 THEN 0 ELSE 1 END"));
  }

  @Test
  public void testSelectColumns() {
    SqlQuery q = new SqlQuery().selectColumns("id", "name", "email").from("users");
    assertEquals("SELECT id, name, email FROM users", q.sql());
  }

  @Test
  public void testSelectDistinct() {
    SqlQuery q = new SqlQuery().selectDistinct("category", "type").from("products");
    assertEquals("SELECT DISTINCT category, type FROM products", q.sql());
  }

  @Test
  public void testRawSqlQuery() {
    SqlQuery q = SqlQuery.raw("SELECT * FROM users WHERE id=?", 123);
    assertEquals("SELECT * FROM users WHERE id=?", q.sql());
    assertEquals(1, q.values().length);
    assertEquals(123, q.values()[0]);
    assertTrue(q.isRaw());
  }

  @Test
  public void testRawSqlWithNullValues() {
    SqlQuery q = SqlQuery.raw("SELECT * FROM users");
    assertEquals(0, q.values().length);
  }

  @Test
  public void testConditionClause() {
    SqlQuery q = new SqlQuery().condition("status IN (?, ?)", "active", "pending");
    assertEquals("WHERE status IN (?, ?)", q.sql());
    assertEquals(2, q.values().length);
  }

  @Test
  public void testPrimaryKey() {
    SqlQuery q = new SqlQuery().primaryKey("user_id");
    assertEquals("user_id", q.getPrimaryKeyName());
  }

  @Test
  public void testComplexWhereWithOrAnd() {
    SqlQuery q =
        new SqlQuery()
            .where("status=?", "active")
            .andWhere("(role=?", "admin")
            .orWhere("role=?)", "moderator");
    assertEquals("WHERE status=? AND (role=? OR role=?)", q.sql());
    assertEquals(3, q.values().length);
  }

  @Test
  public void testNullValuesSanitization() {
    SqlQuery q = new SqlQuery().where("id IS NOT NULL").andWhere("status=?", new Object[] {null});
    assertEquals(0, q.values().length);
  }

  @Test
  public void testEmptyOrderByDirection() {
    SqlQuery q = new SqlQuery().where("id>?", 0).orderBy("name", null);
    assertTrue(q.sql().contains("ORDER BY name asc"));
  }

  @Test
  public void testInvalidSelectColumnsEmpty() {
    try {
      new SqlQuery().selectColumns();
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("At least one column must be specified", e.getMessage());
    }
  }

  @Test
  public void testInvalidSelectColumnsNull() {
    try {
      new SqlQuery().selectColumns((String[]) null);
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("At least one column must be specified", e.getMessage());
    }
  }

  @Test
  public void testInvalidSelectDistinctEmpty() {
    try {
      new SqlQuery().selectDistinct();
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("At least one column must be specified", e.getMessage());
    }
  }

  @Test
  public void testInvalidNegativeLimit() {
    try {
      new SqlQuery().limit(-1);
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Limit must be non-negative", e.getMessage());
    }
  }

  @Test
  public void testInvalidNegativeOffset() {
    try {
      new SqlQuery().offset(-1);
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Offset must be non-negative", e.getMessage());
    }
  }

  @Test
  public void testAndWhereWithoutInitialWhere() {
    try {
      new SqlQuery().andWhere("status=?", "active");
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("andWhere requires at least one preceding where condition", e.getMessage());
    }
  }

  @Test
  public void testOrWhereWithoutInitialWhere() {
    try {
      new SqlQuery().orWhere("status=?", "active");
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("orWhere requires at least one preceding where condition", e.getMessage());
    }
  }

  @Test
  public void testMutateRawQueryThrowsException() {
    SqlQuery q = SqlQuery.raw("SELECT * FROM users");
    try {
      q.where("id=?", 1);
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalStateException");
    } catch (IllegalStateException e) {
      assertEquals("Cannot mutate a raw SqlQuery", e.getMessage());
    }
  }

  @Test
  public void testNullClauseThrowsException() {
    try {
      new SqlQuery().clause(null);
      org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertEquals("Clause must not be null", e.getMessage());
    }
  }

  @Test
  public void testNullSqlInRawQueryThrowsException() {
    try {
      SqlQuery.raw(null);
      org.junit.jupiter.api.Assertions.fail("Should throw NullPointerException");
    } catch (NullPointerException e) {
      assertEquals("SQL must not be null", e.getMessage());
    }
  }
}
