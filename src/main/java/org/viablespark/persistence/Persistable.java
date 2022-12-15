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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;


public interface Persistable {

    Long EMPTY = -1L;

    InstanceData getStoreContainer();

    default Long getId() {
        return getStoreContainer().key.getPrimaryKey().value;
    }

    default Key getKey() {
        return getStoreContainer().key;
    }

    default void setKey(Key complexId) {
        getStoreContainer().key = complexId;
    }

    default boolean isNew() {
        return getKey() == null;
    }

    default void addPropertyChangeListener(
            PropertyChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (getStoreContainer().changeSupport == null) {
            getStoreContainer().changeSupport = getStoreContainer().createPropertyChangeSupport(this);
        }
        getStoreContainer().changeSupport.addPropertyChangeListener(listener);
    }

    default void removePropertyChangeListener(
            PropertyChangeListener listener) {
        getStoreContainer().removePropertyChangeListener(listener);
    }

    default void setPropertyChangeListener(PropertyChangeListener listener) {
        getStoreContainer().setPropertyChangeListener(listener,this);
    }

    default void firePropertyChange(PropertyChangeEvent event) {
        PropertyChangeSupport aChangeSupport = getStoreContainer().changeSupport;
        if (aChangeSupport == null) {
            return;
        }
        aChangeSupport.firePropertyChange(event);
    }

    default void firePropertyChange(String propertyName,
                                    Object oldValue,
                                    Object newValue) {
        PropertyChangeSupport aChangeSupport = getStoreContainer().changeSupport;
        if (aChangeSupport == null) {
            return;
        }
        aChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    default void firePropertyChange(String propertyName,
                                    boolean oldValue,
                                    boolean newValue) {
        PropertyChangeSupport aChangeSupport = getStoreContainer().changeSupport;
        if (aChangeSupport == null) {
            return;
        }
        aChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    default void fireMultiplePropertiesChanged() {
        firePropertyChange(null, null, null);
    }

    default void fireIndexedPropertyChange(String propertyName, int index,
                                           Object oldValue, Object newValue) {
        PropertyChangeSupport aChangeSupport = getStoreContainer().changeSupport;
        if (aChangeSupport == null) {
            return;
        }
        aChangeSupport.fireIndexedPropertyChange(propertyName, index,
                oldValue, newValue);
    }


}
