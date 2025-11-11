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

package org.viablespark.persistence;

import org.viablespark.persistence.dsl.PrimaryKey;

@PrimaryKey("t_key")
public class Task extends Model {

  private String task_name;

  public String getTask_name() {
    return task_name;
  }

  public void setTask_name(String task_name) {
    this.task_name = task_name;
  }
}
