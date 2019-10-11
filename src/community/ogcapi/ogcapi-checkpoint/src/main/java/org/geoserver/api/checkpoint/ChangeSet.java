/* (c) 2019 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.api.checkpoint;

import org.geotools.util.NumberRange;

public class ChangeSet extends ChangeSetSummary {

    private class ScaleOfChangedItems {
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
    }

    ScaleOfChangedItems scaleOfChangedItems;

    public ChangeSet(String checkpoint, long changedItems) {
        super(checkpoint, changedItems);
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
