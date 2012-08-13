/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wps.executor;

import org.geoserver.wps.WPSException;

/**
 * An interface to monitor a process execution.
 * The methods will be called by different threads when working on asynchronous processes,
 * so the usage of thread locals to keep state about an execution is discouraged,
 * 
 * @author Andrea Aime - GeoSolutions
 */
public interface ProcessListener {

    /**
     * Called right before the process is submitted into the {@link ProcessManager}
     * 
     * @param event
     */
    void processSubmitted(ProcessEvent event) throws WPSException;

    /**
     * Called when the process is getting cancelled (mind, this functionality is not currently
     * exposed into the protocol)
     * 
     * @param event
     */
    void processCancelled(ProcessEvent event) throws WPSException;;

    /**
     * Called when the process successfully executed. At this stage the output is yet to be written
     * out, for processes that do streaming computation the actual computation will be done during
     * output encoding)
     */
    void processExecuted(ProcessEvent event) throws WPSException;;

    /**
     * Called when the process successfully executed and the output is succesfully written out to
     * the caller (or stored on disk, for asynchronous calls)
     * 
     * @param event
     */
    void processCompleted(ProcessEvent event) throws WPSException;;

    /**
     * Called when the process failed to execute. This method should not throw further exceptions.
     */
    void processFailed(ProcessEvent event);
}
