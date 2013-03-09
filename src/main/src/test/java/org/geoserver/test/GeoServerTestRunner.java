package org.geoserver.test;

import java.util.Collections;
import java.util.List;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Runner for GeoServer tests. Will run the tests in random order if the "org.geoserver.test.order"
 * system property is set to "random", in the usual JUnit order otherwise
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class GeoServerTestRunner extends BlockJUnit4ClassRunner {

    static final boolean RANDOM_TEST_ORDER;

    static {
        String order = System.getProperty("org.geoserver.test.order");
        RANDOM_TEST_ORDER = "random".equals(order);
    }

    public GeoServerTestRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    protected java.util.List<FrameworkMethod> computeTestMethods() {
        if (RANDOM_TEST_ORDER) {
            List<FrameworkMethod> methods = super.computeTestMethods();
            Collections.shuffle(methods);
            return methods;
        } else {
            return super.computeTestMethods();
        }
    }
}
