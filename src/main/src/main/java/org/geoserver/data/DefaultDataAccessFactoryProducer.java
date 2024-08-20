/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.data;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.Collectors;
import org.geotools.api.data.DataAccessFactory;
import org.geotools.api.data.DataAccessFinder;

public class DefaultDataAccessFactoryProducer implements DataAccessFactoryProducer {

    @Override
    public List<DataAccessFactory> getDataStoreFactories() {
        return Streams.stream(DataAccessFinder.getAvailableDataStores())
                .collect(Collectors.toList());
    }
}
