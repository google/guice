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

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * Created with IntelliJ IDEA.
 * On: 2/06/2007
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @since 1.0
 */
@Entity
public class JpaParentTestEntity {
    private Long id;
    private List<JpaTestEntity> children = new ArrayList<JpaTestEntity>();

    @Id
    @GeneratedValue
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @OneToMany
    public List<JpaTestEntity> getChildren() {
        return children;
    }

    public void setChildren(List<JpaTestEntity> children) {
        this.children = children;
    }
}
