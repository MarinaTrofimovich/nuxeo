/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.apidoc.export.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @since 11.2
 */
public abstract class AbstractExporter implements Exporter {

    protected final ExporterDescriptor descriptor;

    protected String name;

    protected final Map<String, String> properties = new HashMap<>();

    public AbstractExporter(ExporterDescriptor descriptor) {
        this.descriptor = descriptor;
        this.name = descriptor.getId();
        if (descriptor.getProperties() != null) {
            this.properties.putAll(descriptor.getProperties());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public void setProperties(Map<String, String> properties) {
        this.properties.clear();
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

}