/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.importer.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.ImageLayout;

import org.geoserver.catalog.Catalog;
import org.geoserver.importer.ImportContext;
import org.geoserver.importer.ImportTask;
import org.geoserver.importer.ImporterTestSupport;
import org.geoserver.importer.SpatialFile;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.junit.Assume;
import org.junit.Test;

public class GdalTransformTest extends ImporterTestSupport {

    @Test
    public void testGdalTranslateTrasform() throws Exception {
        Assume.assumeTrue(GdalTranslateTransform.isAvailable());

        File dir = unpack("geotiff/EmissiveCampania.tif.bz2");
        File tif = new File(dir, "EmissiveCampania.tif");

        ImportContext context = importer.createContext(new SpatialFile(tif));
        assertEquals(1, context.getTasks().size());

        ImportTask task = context.getTasks().get(0);
        assertEquals(ImportTask.State.READY, task.getState());

        // setup the transformation
        GdalTranslateTransform gtx = buildGdalTranslate();
        task.getTransform().add(gtx);

        assertEquals("EmissiveCampania", task.getLayer().getResource().getName());

        importer.run(context);

        Catalog cat = getCatalog();
        assertNotNull(cat.getLayerByName("EmissiveCampania"));

        assertEquals(ImportTask.State.COMPLETE, task.getState());

        runChecks("EmissiveCampania");
        
        // check we did the gdal_transform on the file
        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(tif);
            ImageLayout layout = reader.getImageLayout();
            ColorModel cm = layout.getColorModel(null);
            assertEquals(3, cm.getNumComponents());
            SampleModel sm = layout.getSampleModel(null);
            assertEquals(3, sm.getNumBands());
            assertEquals(DataBuffer.TYPE_BYTE, sm.getDataType());
            assertEquals(0, reader.getNumOverviews());
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
        
    }

    @Test
    public void testGdalAddo() throws Exception {
        Assume.assumeTrue(GdalAddoTransform.isAvailable());
        File dir = unpack("geotiff/EmissiveCampania.tif.bz2");
        File tif = new File(dir, "EmissiveCampania.tif");

        ImportContext context = importer.createContext(new SpatialFile(tif));
        assertEquals(1, context.getTasks().size());

        ImportTask task = context.getTasks().get(0);
        assertEquals(ImportTask.State.READY, task.getState());

        // setup the transformation
        GdalAddoTransform gad = buildGdalAddo();
        task.getTransform().add(gad);

        assertEquals("EmissiveCampania", task.getLayer().getResource().getName());

        importer.run(context);

        Catalog cat = getCatalog();
        assertNotNull(cat.getLayerByName("EmissiveCampania"));

        assertEquals(ImportTask.State.COMPLETE, task.getState());

        runChecks("EmissiveCampania");

        // check we did the gdaladdo on the file
        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(tif);
            ImageLayout layout = reader.getImageLayout();
            ColorModel cm = layout.getColorModel(null);
            assertEquals(16, cm.getNumComponents());
            SampleModel sm = layout.getSampleModel(null);
            assertEquals(16, sm.getNumBands());
            assertEquals(DataBuffer.TYPE_USHORT, sm.getDataType());
            assertEquals(3, reader.getNumOverviews());
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }

    }



    @Test
    public void testTranslateAddo() throws Exception {
        Assume.assumeTrue(GdalTranslateTransform.isAvailable());
        Assume.assumeTrue(GdalAddoTransform.isAvailable());

        File dir = unpack("geotiff/EmissiveCampania.tif.bz2");
        File tif = new File(dir, "EmissiveCampania.tif");

        ImportContext context = importer.createContext(new SpatialFile(tif));
        assertEquals(1, context.getTasks().size());

        ImportTask task = context.getTasks().get(0);
        assertEquals(ImportTask.State.READY, task.getState());

        GdalTranslateTransform gtx = buildGdalTranslate();
        task.getTransform().add(gtx);
        GdalAddoTransform gad = buildGdalAddo();
        task.getTransform().add(gad);

        assertEquals("EmissiveCampania", task.getLayer().getResource().getName());

        importer.run(context);

        Catalog cat = getCatalog();
        assertNotNull(cat.getLayerByName("EmissiveCampania"));

        assertEquals(ImportTask.State.COMPLETE, task.getState());

        runChecks("EmissiveCampania");

        // check we did the gdal_transform and gdaladdo on the file
        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(tif);
            ImageLayout layout = reader.getImageLayout();
            ColorModel cm = layout.getColorModel(null);
            assertEquals(3, cm.getNumComponents());
            SampleModel sm = layout.getSampleModel(null);
            assertEquals(3, sm.getNumBands());
            assertEquals(DataBuffer.TYPE_BYTE, sm.getDataType());
            assertEquals(3, reader.getNumOverviews());
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }

    }


    private GdalTranslateTransform buildGdalTranslate() {
        List<String> options = new ArrayList<>();
        options.add("-b");
        options.add("1");
        options.add("-b");
        options.add("2");
        options.add("-b");
        options.add("3");
        options.add("-ot");
        options.add("Byte");
        GdalTranslateTransform gtx = new GdalTranslateTransform(options);
        return gtx;
    }

    private GdalAddoTransform buildGdalAddo() {
        List<String> options = new ArrayList<>();
        options.add("-r");
        options.add("average");
        List<Integer> levels = new ArrayList<>();
        levels.add(2);
        levels.add(4);
        levels.add(8);
        GdalAddoTransform gad = new GdalAddoTransform(options, levels);
        return gad;
    }

}
