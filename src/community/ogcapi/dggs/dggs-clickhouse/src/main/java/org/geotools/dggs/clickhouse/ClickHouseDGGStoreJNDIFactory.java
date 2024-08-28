/* (c) 2024 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geotools.dggs.clickhouse;

public class ClickHouseDGGStoreJNDIFactory extends ClickHouseDGGStoreFactory {

    public ClickHouseDGGStoreJNDIFactory() {
        this.delegate = new ClickHouseJDBCJNDIDataStoreFactory();
    }

    @Override
    public String getDisplayName() {
        return "ClickHouse DGGS integration (JNDI)";
    }

    @Override
    public String getDescription() {
        return "ClickHouse DGGS integration (JNDI)";
    }
}
