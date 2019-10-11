/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

import java.util.ArrayList;
import java.util.List;

/** Summary of changesets according to the Testbed 15 Delta updates */
public class ChangeSetSummary {

    enum Priority {
        high,
        medium,
        low
    };

    static class ChangedItem {
        Priority priority;
        long count;

        public ChangedItem(Priority priority, long count) {
            this.priority = priority;
            this.count = count;
        }

        public Priority getPriority() {
            return priority;
        }

        public long getCount() {
            return count;
        }
    }

    String checkpoint;
    List<ChangedItem> summaryOfChangedItems = new ArrayList<>();

    public ChangeSetSummary(String checkpoint, long changedItems) {
        this.checkpoint = checkpoint;
        this.summaryOfChangedItems.add(new ChangedItem(Priority.medium, changedItems));
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(String checkpoint) {
        this.checkpoint = checkpoint;
    }

    public List<ChangedItem> getSummaryOfChangedItems() {
        return summaryOfChangedItems;
    }

    public void setSummaryOfChangedItems(List<ChangedItem> summaryOfChangedItems) {
        this.summaryOfChangedItems = summaryOfChangedItems;
    }
}
