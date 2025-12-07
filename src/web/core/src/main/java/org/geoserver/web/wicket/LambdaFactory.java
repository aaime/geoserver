/* (c) 2025 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.wicket;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.link.Link;

/**
 * Factory for creating Wicket components with lambda-based action handlers. Simplifies component construction by
 * allowing inline definition of component behavior using method references or lambda expressions.
 *
 * <p>TODO: List of other common references that can be added: - LoadableDetachableModel - ListView -
 * GeoServerTablePanel - Ajax links often receive a lambda showing a help dialog, can it be centralized too?
 */
public class LambdaFactory {

    private LambdaFactory() {
        // Utility class - prevent instantiation
    }

    // ==================== Serializable Functional Interfaces ====================

    /**
     * A serializable variant of {@link Consumer} for use in Wicket components. Required because Wicket components are
     * serializable and any captured lambdas must be serializable as well.
     *
     * @param <T> the type of the input to the operation
     */
    @FunctionalInterface
    public interface SerializableConsumer<T> extends Consumer<T>, Serializable {}

    /**
     * A serializable variant of {@link Supplier} for use in Wicket components.
     *
     * @param <T> the type of results supplied by this supplier
     */
    @FunctionalInterface
    public interface SerializableSupplier<T> extends Supplier<T>, Serializable {}

    /** A serializable variant of {@link Runnable} for use in Wicket components. */
    @FunctionalInterface
    public interface SerializableRunnable extends Runnable, Serializable {}

    // ==================== Action Components ====================

    /**
     * Creates a button with the specified action handler.
     *
     * @param id the component id
     * @param onSubmit the action to execute when the button is submitted
     * @return a new Button instance
     * @example
     *     <pre>
     * add(LambdaFactory.button("save", this::handleSave));
     * </pre>
     */
    public static Button button(String id, SerializableRunnable onSubmit) {
        return new Button(id) {
            @Override
            public void onSubmit() {
                onSubmit.run();
            }
        };
    }

    /**
     * Creates a button with the specified action handler and additional configuration.
     *
     * @param id the component id
     * @param onSubmit the action to execute when the button is submitted
     * @param configurator lambda to configure additional button properties
     * @return a new Button instance
     * @example
     *     <pre>
     * add(LambdaFactory.button("save", this::handleSave, btn -> {
     *     btn.setDefaultFormProcessing(false);
     *     btn.setOutputMarkupId(true);
     * }));
     * </pre>
     */
    public static Button button(String id, SerializableRunnable onSubmit, SerializableConsumer<Button> configurator) {
        Button button = new Button(id) {
            @Override
            public void onSubmit() {
                onSubmit.run();
            }
        };
        configurator.accept(button);
        return button;
    }

    /**
     * Creates a link with the specified click handler.
     *
     * @param id the component id
     * @param onClick the action to execute when the link is clicked
     * @return a new Link instance
     * @example
     *     <pre>
     * add(LambdaFactory.link("cancel", this::handleCancel));
     * </pre>
     */
    public static Link<Void> link(String id, SerializableRunnable onClick) {
        return new Link<>(id) {
            @Override
            public void onClick() {
                onClick.run();
            }
        };
    }

    /**
     * Creates an ajax link with the specified click handler.
     *
     * @param id the component id
     * @param onClick the action to execute when the link is clicked
     * @return a new Link instance
     * @example
     *     <pre>
     * add(LambdaFactory.link("cancel", this::handleCancel));
     * </pre>
     */
    public static AjaxLink<Void> ajaxLink(String id, SerializableConsumer<AjaxRequestTarget> onClick) {
        return new AjaxLink<>(id) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                onClick.accept(target);
            }
        };
    }

    /**
     * Creates an ajax link with the specified click handler.
     *
     * @param id the component id
     * @param onClick the action to execute when the link is clicked
     * @return a new Link instance
     * @example
     *     <pre>
     * add(LambdaFactory.link("cancel", this::handleCancel));
     * </pre>
     */
    public static AjaxLink<Void> ajaxLink(String id, SerializableRunnable onClick) {
        return new AjaxLink<>(id) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                onClick.run();
            }
        };
    }
}
