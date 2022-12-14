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

import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProposalMapper implements PersistableMapper<Proposal> {

    @Override
    public Proposal mapRow(ResultSet rs, int rowNum) throws SQLException {
        var pr = new Proposal();
        pr.setKey(Key.of("pr_key",rs.getLong("pr_key")));
        pr.setDistance(rs.getInt("dist"));
        return pr;
    }

    @Override
    public Proposal mapRow(SqlRowSet rs, int rowNum) {
        return null;
    }
}
