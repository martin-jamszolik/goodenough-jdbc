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

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.viablespark.persistence.BaseRepository;
import org.viablespark.persistence.dsl.SqlQuery;

/** Repository for {@link SupplierCategory}. */
public class SupplierCategoryRepository extends BaseRepository<SupplierCategory> {

  public SupplierCategoryRepository(JdbcTemplate db) {
    super(db);
  }

  public List<SupplierCategory> all() {
    return queryEntity(
        new SqlQuery().orderBy("scat_name", SqlQuery.Direction.ASC), SupplierCategory.class);
  }
}
