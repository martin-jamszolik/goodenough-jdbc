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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.ResultSetWrappingSqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.lang.NonNullApi;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface PersistableMapper<E extends Persistable> extends RowMapper<E> {


    @Override
    @Nullable
    default E mapRow(ResultSet rs, int rowNum) throws SQLException {
        return mapRow(new ResultSetWrappingSqlRowSet(rs), rowNum);
    }

    @Nullable
    E mapRow(SqlRowSet rs, int rowNum);
    
}
