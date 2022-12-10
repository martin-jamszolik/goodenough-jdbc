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


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class InstanceData {

    Key key;
    transient PropertyChangeSupport changeSupport;

    public final synchronized PropertyChangeSupport createPropertyChangeSupport(final Object bean) {
        return new PropertyChangeSupport(bean);
    }

    public final synchronized void removePropertyChangeListener(
            PropertyChangeListener listener) {
        if (listener == null || changeSupport == null) {
            return;
        }
        changeSupport.removePropertyChangeListener(listener);
    }

    public final synchronized void setPropertyChangeListener(PropertyChangeListener listener, final Object bean) {
        if (listener == null) {
            return;
        }

        if (changeSupport == null) {
            changeSupport = createPropertyChangeSupport(bean);
        }

        changeSupport.removePropertyChangeListener(listener);
        changeSupport.addPropertyChangeListener(listener);
    }
}

