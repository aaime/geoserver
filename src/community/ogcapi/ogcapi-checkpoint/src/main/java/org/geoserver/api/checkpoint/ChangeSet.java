/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.NumberRange;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Summary of changesets according to the Testbed 15 Delta updates */
public class ChangeSet {

    enum Priority {
        high,
        medium,
        low
    };

    public static class ChangedItem {
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

        @Override
        public String toString() {
            return "ChangedItem{" +
                    "priority=" + priority +
                    ", count=" + count +
                    '}';
        }
    }

    public static class ScaleOfChangedItems {
        Double minScaleDenominator;
        Double maxScaleDenominator;

        public ScaleOfChangedItems(Double minScaleDenominator, Double maxScaleDenominator) {
            if (!Double.isNaN(minScaleDenominator) && !Double.isNaN(maxScaleDenominator)) {
                this.minScaleDenominator = minScaleDenominator;
            }
            if (!Double.isNaN(maxScaleDenominator) && !Double.isInfinite(maxScaleDenominator)) {
                this.maxScaleDenominator = maxScaleDenominator;
            }
        }

        public Double getMinScaleDenominator() {
            return minScaleDenominator;
        }

        public Double getMaxScaleDenominator() {
            return maxScaleDenominator;
        }

        @Override
        public String toString() {
            return "ScaleOfChangedItems{" +
                    "minScaleDenominator=" + minScaleDenominator +
                    ", maxScaleDenominator=" + maxScaleDenominator +
                    '}';
        }
    }

    String checkpoint;
    List<ChangedItem> summaryOfChangedItems = new ArrayList<>();
    List<BoundsAndCRS> extentOfChangedItems;
    ScaleOfChangedItems scaleOfChangedItems;

    public ChangeSet(
            String checkpoint, List<ReferencedEnvelope> extentOfChangedItems, long changedItems) {
        this.checkpoint = checkpoint;
        this.summaryOfChangedItems.add(new ChangedItem(Priority.medium, changedItems));
        this.extentOfChangedItems =
                extentOfChangedItems.stream()
                        .map(re -> new BoundsAndCRS(re))
                        .collect(Collectors.toList());
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

    public List<BoundsAndCRS> getExtentOfChangedItems() {
        return extentOfChangedItems;
    }

    public ScaleOfChangedItems getScaleOfChangedItems() {
        return scaleOfChangedItems;
    }

    public void setScaleOfChangedItems(NumberRange<Double> scales) {
        this.scaleOfChangedItems =
                new ScaleOfChangedItems(scales.getMinimum(), scales.getMaximum());
    }

    public void setScaleOfChangedItems(ScaleOfChangedItems scaleOfChangedItems) {
        this.scaleOfChangedItems = scaleOfChangedItems;
    }
    
    
}


