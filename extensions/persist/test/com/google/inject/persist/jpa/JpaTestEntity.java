/**
 * Copyright (C) 2010 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.persist.jpa;

import com.google.inject.persist.finder.Finder;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQuery;

/**
 * Created with IntelliJ IDEA.
 * On: 2/06/2007
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @since 1.0
 */
@Entity
@NamedQuery(name = JpaTestEntity.LIST_ALL_QUERY, query = "from JpaTestEntity")
public class JpaTestEntity {
    private Long id;
    private String text;
    public static final String LIST_ALL_QUERY = "JpaTestEntity.listAll";

    @Id
    @GeneratedValue
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Finder(query = "from JpaTestEntity")
    public List<JpaTestEntity> listAll() { return null; }
}
