/* (c) 2026 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.security;

import static org.junit.Assert.assertEquals;

import org.geotools.api.filter.Filter;
import org.geotools.filter.text.ecql.ECQL;
import org.junit.Test;

public class NormalizingFilterVisitorTest {

    static final NormalizingFilterVisitor VISITOR = new NormalizingFilterVisitor();

    private static Filter normalize(Filter f) {
        return (Filter) f.accept(VISITOR, null);
    }

    @Test
    public void testEqualLiteralLeftSwapped() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 = foo"));
        Filter expected = ECQL.toFilter("foo = 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testGreaterThanLiteralLeftSwapped() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 > foo"));
        Filter expected = ECQL.toFilter("foo < 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testGreaterThanOrEqualLiteralLeftSwapped() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 >= foo"));
        Filter expected = ECQL.toFilter("foo <= 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testLessThanLiteralLeftSwapped() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 < foo"));
        Filter expected = ECQL.toFilter("foo > 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testLessThanOrEqualLiteralLeftSwapped() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 <= foo"));
        Filter expected = ECQL.toFilter("foo >= 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testPropertyLeftUnchanged() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("foo = 13"));
        Filter expected = ECQL.toFilter("foo = 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testAndOperandsSortedRegardlessOfInputOrder() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("foo = 1 AND bar = 2"));
        Filter expected = ECQL.toFilter("bar = 2 AND foo = 1");
        assertEquals(expected, normalized);
    }

    @Test
    public void testOrOperandsSortedRegardlessOfInputOrder() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("foo = 1 OR bar = 2"));
        Filter expected = ECQL.toFilter("bar = 2 OR foo = 1");
        assertEquals(expected, normalized);
    }

    @Test
    public void testAndWithLiteralLeftSwappedAndSorted() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("13 = foo AND bar = 'x'"));
        Filter expected = ECQL.toFilter("bar = 'x' AND foo = 13");
        assertEquals(expected, normalized);
    }

    @Test
    public void testOrWithThreeOperandsSorted() throws Exception {
        // normalize flattens nested OR (via SimplifyingFilterVisitor), so expected must also be normalized
        Filter expected = normalize(ECQL.toFilter("a = 1 OR b = 2 OR c = 3"));
        assertEquals(expected, normalize(ECQL.toFilter("c = 3 OR a = 1 OR b = 2")));
        assertEquals(expected, normalize(ECQL.toFilter("b = 2 OR c = 3 OR a = 1")));
    }

    @Test
    public void testInFunctionValuesAreSorted() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("in3(foo,'c','a','b') = true"));
        Filter expected = ECQL.toFilter("in3(foo,'a','b','c') = true");
        assertEquals(expected, normalized);
    }

    @Test
    public void testInFunctionAlreadySortedUnchanged() throws Exception {
        Filter normalized = normalize(ECQL.toFilter("in3(foo,'a','b','c') = true"));
        Filter expected = ECQL.toFilter("in3(foo,'a','b','c') = true");
        assertEquals(expected, normalized);
    }
}
