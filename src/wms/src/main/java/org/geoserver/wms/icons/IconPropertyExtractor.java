/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wms.icons;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.geotools.filter.visitor.IsStaticExpressionVisitor;
import org.geotools.renderer.style.ExpressionExtractor;
import org.geotools.styling.ExternalGraphic;
import org.geotools.styling.Fill;
import org.geotools.styling.Graphic;
import org.geotools.styling.LineSymbolizer;
import org.geotools.styling.Mark;
import org.geotools.styling.PointSymbolizer;
import org.geotools.styling.PolygonSymbolizer;
import org.geotools.styling.Stroke;
import org.geotools.styling.Style;
import org.geotools.styling.Symbolizer;
import org.geotools.styling.TextSymbolizer2;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.expression.Expression;
import org.opengis.style.GraphicalSymbol;

/**
 * Utility to extract the values of dynamic properties from a style when applied to a particular
 * feature.
 *
 * @see IconPropertyInjector
 * @author David Winslow, OpenGeo
 */
public final class IconPropertyExtractor {
    public static final String NON_POINT_GRAPHIC_KEY = "npg";
    private List<List<MiniRule>> style;

    private IconPropertyExtractor(List<List<MiniRule>> style) {
        this.style = style;
    }

    private IconProperties propertiesFor(SimpleFeature feature) {
        return new FeatureProperties(feature).properties();
    }

    public static IconProperties extractProperties(
            List<List<MiniRule>> style, SimpleFeature feature) {
        return new IconPropertyExtractor(style).propertiesFor(feature);
    }

    public static IconProperties extractProperties(Style style, SimpleFeature feature) {
        return new IconPropertyExtractor(MiniRule.minify(style)).propertiesFor(feature);
    }

    /** Extracts a Graphic object from the given symbolizer */
    public static Graphic getGraphic(Symbolizer symbolizer, boolean includeNonPointGraphics) {
        // try point first
        if (symbolizer instanceof PointSymbolizer) {
            return ((PointSymbolizer) symbolizer).getGraphic();
        }
        if (!includeNonPointGraphics) {
            return null;
        }
        // try in other symbolizers
        if (symbolizer instanceof PolygonSymbolizer) {
            final Fill fill = ((PolygonSymbolizer) symbolizer).getFill();
            if (fill != null && fill.getGraphicFill() != null) {
                return fill.getGraphicFill();
            }
            final Stroke stroke = ((PolygonSymbolizer) symbolizer).getStroke();
            if (stroke != null) {
                return stroke.getGraphicStroke();
            }
        } else if (symbolizer instanceof LineSymbolizer) {
            final Stroke stroke = ((LineSymbolizer) symbolizer).getStroke();
            if (stroke != null) {
                return stroke.getGraphicStroke();
            }
        } else if (symbolizer instanceof TextSymbolizer2) {
            return ((TextSymbolizer2) symbolizer).getGraphic();
        }

        return null;
    }

    private class FeatureProperties {
        private static final String URL = ".url";
        private static final String WIDTH = ".width";
        private static final String LINEJOIN = ".linejoin";
        private static final String LINECAP = ".linecap";
        private static final String DASHOFFSET = ".dashoffset";
        private static final String GRAPHIC = ".graphic";
        private static final String COLOR = ".color";
        private static final String STROKE = ".stroke";
        private static final String FILL = ".fill";
        private static final String NAME = ".name";
        private static final String SIZE = ".size";
        private static final String ROTATION = ".rotation";
        private static final String OPACITY = ".opacity";

        private final SimpleFeature feature;

        public FeatureProperties(SimpleFeature feature) {
            this.feature = feature;
        }
        /**
         * Safe expression execution with default fallback.
         *
         * @param expression
         * @param feature
         * @param defaultValue
         * @return evaluated value or defaultValue if unavailable
         */
        private Double evaluate(Expression expression, SimpleFeature feature, double defaultValue) {
            if (expression == null) {
                return defaultValue;
            }
            Double value = expression.evaluate(feature, Double.class);
            if (value == null || Double.isNaN(value)) {
                return defaultValue;
            }
            return value;
        }

        public IconProperties properties() {
            IconProperties singleExternalGraphic = trySingleExternalGraphic();
            if (singleExternalGraphic != null) {
                return singleExternalGraphic;
            } else {
                return embeddedIconProperties();
            }
        }

        public IconProperties trySingleExternalGraphic() {
            MiniRule singleRule = null;
            for (List<MiniRule> rules : style) {
                boolean applied = false;

                for (MiniRule rule : rules) {
                    final boolean applicable =
                            (rule.isElseFilter && !applied)
                                    || (rule.filter == null)
                                    || (rule.filter.evaluate(feature));
                    if (applicable) {
                        if (singleRule == null) {
                            singleRule = rule;
                        } else {
                            return null;
                        }
                    }
                }
            }
            if (singleRule == null) {
                return null;
            }
            return isExternalGraphic(singleRule);
        }

        public IconProperties isExternalGraphic(MiniRule rule) {
            if (rule.symbolizers.size() != 1) {
                return null;
            }
            Graphic g = getGraphic(rule.symbolizers.get(0), true);
            if (g == null) {
                return null;
            }
            if (g.graphicalSymbols().size() != 1) {
                return null;
            }
            GraphicalSymbol gSym = g.graphicalSymbols().get(0);
            if (!(gSym instanceof ExternalGraphic)) {
                return null;
            }
            ExternalGraphic exGraphic = (ExternalGraphic) gSym;
            try {
                Double opacity = evaluate(g.getOpacity(), feature, 1.0);
                Double size = 1d * Icons.getExternalSize(exGraphic, feature);
                if (size != null) size = size / Icons.DEFAULT_SYMBOL_SIZE;
                Double rotation = evaluate(g.getRotation(), feature, 0.0);
                Expression urlExpression =
                        ExpressionExtractor.extractCqlExpressions(
                                exGraphic.getLocation().toExternalForm());
                return IconProperties.externalReference(
                        opacity, size, rotation, urlExpression.evaluate(feature, String.class));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        public IconProperties embeddedIconProperties() {
            Map<String, String> props = new TreeMap<>();
            Double size = null;
            boolean allRotated = true;
            boolean nonPointGraphic = false;
            for (int i = 0; i < style.size(); i++) {
                List<MiniRule> rules = style.get(i);
                for (int j = 0; j < rules.size(); j++) {
                    MiniRule rule = rules.get(j);
                    final boolean matches;
                    if (rule.filter == null) {
                        matches = !rule.isElseFilter || props.isEmpty();
                    } else {
                        matches = rule.filter.evaluate(feature);
                    }

                    if (matches) {
                        for (int k = 0; k < rule.symbolizers.size(); k++) {
                            props.put(i + "." + j + "." + k, "");
                            Symbolizer sym = rule.symbolizers.get(k);
                            Graphic graphic = getGraphic(sym, false);
                            if (graphic == null) {
                                graphic = getGraphic(sym, true);
                                nonPointGraphic |= graphic != null;
                            }
                            if (graphic != null) {
                                addGraphicProperties(i + "." + j + "." + k, graphic, props);
                                final Double gRotation = graphicRotation(graphic);
                                allRotated &= gRotation != null;

                                final Double gSize = Icons.graphicSize(graphic, gRotation, feature);
                                if (size == null || (gSize != null && gSize > size)) {
                                    size = gSize;
                                }
                            }
                        }
                    }
                }
            }

            if (nonPointGraphic) {
                props.put(NON_POINT_GRAPHIC_KEY, "true");
            }

            if (size != null) size = size / 16d;
            // If all the symbols in the stack were rotated, force it to be oriented to a bearing.
            final Double rotation = allRotated ? 0d : null;
            return IconProperties.generator(null, size, rotation, props);
        }

        public boolean isStatic(Expression ex) {
            return (Boolean) ex.accept(IsStaticExpressionVisitor.VISITOR, null);
        }

        private Double graphicRotation(Graphic g) {
            if (g.getRotation() != null) {
                return g.getRotation().evaluate(feature, Double.class);
            } else {
                return null;
            }
        }

        public void addGraphicProperties(String prefix, Graphic g, Map<String, String> props) {
            if (g.getOpacity() != null && !isStatic(g.getOpacity())) {
                props.put(prefix + OPACITY, g.getOpacity().evaluate(feature, String.class));
            }
            if (g.getRotation() != null && !isStatic(g.getRotation())) {
                props.put(prefix + ROTATION, g.getRotation().evaluate(feature, String.class));
            }
            if (g.getSize() != null && !isStatic(g.getSize())) {
                props.put(prefix + SIZE, g.getSize().evaluate(feature, String.class));
            }
            if (!g.graphicalSymbols().isEmpty()) {
                if (g.graphicalSymbols().get(0) instanceof Mark) {
                    Mark mark = (Mark) g.graphicalSymbols().get(0);
                    addMarkProperties(prefix, mark, props);
                } else if (g.graphicalSymbols().get(0) instanceof ExternalGraphic) {
                    ExternalGraphic exGraphic = (ExternalGraphic) g.graphicalSymbols().get(0);
                    addExternalGraphicProperties(prefix, exGraphic, props);
                }
            }
        }

        public void addMarkProperties(String prefix, Mark mark, Map<String, String> props) {
            if (mark.getWellKnownName() != null && !isStatic(mark.getWellKnownName())) {
                props.put(prefix + NAME, mark.getWellKnownName().evaluate(feature, String.class));
            }
            if (mark.getFill() != null) {
                addFillProperties(prefix + FILL, mark.getFill(), props);
            }
            if (mark.getStroke() != null) {
                addStrokeProperties(prefix + STROKE, mark.getStroke(), props);
            }
        }

        public void addFillProperties(String prefix, Fill fill, Map<String, String> props) {
            if (fill.getColor() != null && !isStatic(fill.getColor())) {
                props.put(prefix + COLOR, fill.getColor().evaluate(feature, String.class));
            }
            if (fill.getOpacity() != null && !isStatic(fill.getOpacity())) {
                props.put(prefix + OPACITY, fill.getOpacity().evaluate(feature, String.class));
            }
            if (fill.getGraphicFill() != null) {
                addGraphicProperties(prefix + GRAPHIC, fill.getGraphicFill(), props);
            }
        }

        public void addStrokeProperties(String prefix, Stroke stroke, Map<String, String> props) {
            if (stroke.getColor() != null && !isStatic(stroke.getColor())) {
                props.put(prefix + COLOR, stroke.getColor().evaluate(feature, String.class));
            }
            if (stroke.getDashOffset() != null && !isStatic(stroke.getDashOffset())) {
                props.put(
                        prefix + DASHOFFSET,
                        stroke.getDashOffset().evaluate(feature, String.class));
            }
            if (stroke.getLineCap() != null && !isStatic(stroke.getLineCap())) {
                props.put(prefix + LINECAP, stroke.getLineCap().evaluate(feature, String.class));
            }
            if (stroke.getLineJoin() != null && !isStatic(stroke.getLineJoin())) {
                props.put(prefix + LINEJOIN, stroke.getLineJoin().evaluate(feature, String.class));
            }
            if (stroke.getOpacity() != null && !isStatic(stroke.getOpacity())) {
                props.put(prefix + OPACITY, stroke.getOpacity().evaluate(feature, String.class));
            }
            if (stroke.getWidth() != null && !isStatic(stroke.getWidth())) {
                props.put(prefix + WIDTH, stroke.getWidth().evaluate(feature, String.class));
            }
            if (stroke.getGraphicStroke() != null) {
                addGraphicProperties(prefix + GRAPHIC, stroke.getGraphicStroke(), props);
            }
            if (stroke.getGraphicFill() != null) {
                addGraphicProperties(prefix + GRAPHIC, stroke.getGraphicFill(), props);
            }
        }

        public void addExternalGraphicProperties(
                String prefix, ExternalGraphic exGraphic, Map<String, String> props) {
            try {
                Expression ex =
                        ExpressionExtractor.extractCqlExpressions(
                                exGraphic.getLocation().toExternalForm());
                if (!isStatic(ex)) {
                    props.put(prefix + URL, ex.evaluate(feature, String.class));
                }
            } catch (MalformedURLException e) {
                // Do nothing, it's just an icon we can't resolve.
                // TODO: Log at FINER or FINEST level?
            }
        }
    }
}
