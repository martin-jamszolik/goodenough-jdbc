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

import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;

/*
 * Contractor entity demonstrates that you don't have to extend from Model.
 * There are situations where you cannot extend your object.
 * Use the interface and provide an instance of Data object to hold keys.
 */
@PrimaryKey("sc_key")
public class Contractor implements Persistable {
  private Key key = Key.None;
  private String name;
  private String contact;
  private String phone1;
  private String fax;
  private String email;

  @Override
  public Key getRefs() {
    return key;
  }

  @Override
  public void setRefs(Key refs) {
    this.key = refs;
  }

  public Contractor() {}

  public Contractor(String key, Long id) {
    setRefs(Key.of(key, id));
  }

  @Named("sc_name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getContact() {
    return contact;
  }

  public void setContact(String contact) {
    this.contact = contact;
  }

  public String getPhone1() {
    return phone1;
  }

  public void setPhone1(String phone1) {
    this.phone1 = phone1;
  }

  public String getFax() {
    return fax;
  }

  public void setFax(String fax) {
    this.fax = fax;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
