/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wps.executor;

import java.util.Map;

import net.opengis.wps10.ExecuteType;

/**
 * The context of an event triggered in a {@link ProcessListener}
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class ProcessEvent implements Cloneable {

    String executionId;

    ExecuteType request;

    boolean asynchronous;

    boolean chained;

    Map<String, Object> inputs;

    Map<String, Object> outputs;

    Throwable error;

    /**
     * Constructor used when starting up a process
     * 
     * @param executionId
     * @param request
     * @param asynchronous
     * @param chained
     * @param inputs
     */
    public ProcessEvent(String executionId, ExecuteType request, boolean asynchronous,
            boolean chained, Map<String, Object> inputs) {
        this.executionId = executionId;
        this.request = request;
        this.asynchronous = asynchronous;
        this.chained = chained;
        this.inputs = inputs;
    }

    /**
     * Constructor used when a process sucessfully executed
     * 
     * @param executionId
     * @param request
     * @param asynchronous
     * @param chained
     * @param inputs
     * @param outputs
     */
    public ProcessEvent(String executionId, ExecuteType request, boolean asynchronous,
            boolean chained, Map<String, Object> inputs, Map<String, Object> outputs) {
        this.executionId = executionId;
        this.request = request;
        this.asynchronous = asynchronous;
        this.chained = chained;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    /**
     * 
     * @param executionId
     * @param request
     * @param asynchronous
     * @param chained
     * @param inputs
     * @param exception
     */
    public ProcessEvent(String executionId, ExecuteType request, boolean asynchronous,
            boolean chained, Map<String, Object> inputs, Exception exception) {
        super();
        this.executionId = executionId;
        this.request = request;
        this.asynchronous = asynchronous;
        this.chained = chained;
        this.inputs = inputs;
        this.error = exception;
    }

    /**
     * The execution id for the current process (in case the process is chained the execution id
     * will be the one of the parent process). All top level processes have an execution id, even if
     * they are not called in an asynchronous fashion.
     * 
     * @return
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * The original WPS requset for this process
     * 
     * @return
     */
    public ExecuteType getRequest() {
        return request;
    }

    /**
     * Returns true if the process is being executed in asynchronous mode
     * 
     * @return
     */
    public boolean isAsynchronous() {
        return asynchronous;
    }

    /**
     * Returns true if the process is executed as the input of another process (chaining)
     * 
     * @return
     */
    public boolean isChained() {
        return chained;
    }

    /**
     * Returns the inputs of the process. Mind, the inputs are going to be parsed on demand, so
     * asking for a certain input might trigger network access, a internal WFS/WCS request, or start
     * a chained execution
     * 
     * @return
     */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /**
     * The process outputs, available only if the process successfully executed.
     * 
     * @return
     */
    public Map<String, Object> getOutputs() {
        return outputs;
    }

    /**
     * The failure exception, available only if the process failed to execute
     * 
     * @return
     */
    public Throwable getError() {
        return error;
    }

    public ProcessEvent clone() {
        try {
            return (ProcessEvent) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(
                    "Unexpected, clone did not work even if we implement cloneable??", e);
        }
    }
}
