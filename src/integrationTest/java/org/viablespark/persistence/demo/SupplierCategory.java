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

import org.viablespark.persistence.Model;
import org.viablespark.persistence.dsl.PrimaryKey;

/**
 * Category of suppliers in the {@code demo} database ({@code supplier_category}).
 *
 * <p>Derived per {@code docs/from-schema.md}: single auto-increment PK, one data column. Table and
 * column names follow the plain camelCase to snake_case convention, so no {@code @Named}
 * annotations are needed.
 */
@PrimaryKey("scat_key")
public class SupplierCategory extends Model {

  private String scatName;

  public String getScatName() {
    return scatName;
  }

  public void setScatName(String scatName) {
    this.scatName = scatName;
  }
}
