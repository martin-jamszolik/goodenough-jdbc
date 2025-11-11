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

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.support.rowset.SqlRowSet;

public class ProposalMapper implements PersistableMapper<Proposal> {

  private final Map<Key, Contractor> store = new LinkedHashMap<>();

  @Override
  public Proposal mapRow(SqlRowSet rs, int rowNum) {
    var pr = new Proposal();
    pr.setRefs(Key.of("pr_key", rs.getLong("pr_key")));
    pr.setDistance(rs.getInt("dist"));
    pr.setPropName(rs.getString("proposal_name"));
    if (rs.getObject("sc_key") != null) {
      var contractor =
          store.computeIfAbsent(
              Key.of("sc_key", rs.getLong("sc_key")),
              sc -> {
                var ct = new Contractor("sc_key", rs.getLong("sc_key"));
                ct.setContact(rs.getString("contact"));
                ct.setFax(rs.getString("fax"));
                ct.setEmail(rs.getString("email"));
                ct.setPhone1(rs.getString("phone1"));
                return ct;
              });
      pr.setContractor(contractor);
    }
    return pr;
  }

  public Map<Key, Contractor> getContractors() {
    return store;
  }
}
