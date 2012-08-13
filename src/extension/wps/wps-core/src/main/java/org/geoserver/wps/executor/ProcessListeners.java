/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wps.executor;

import java.util.List;

import org.geoserver.ows.AbstractDispatcherCallback;
import org.geoserver.ows.Request;
import org.geoserver.platform.GeoServerExtensions;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Notifies the process listeners of events happening around processes
 */
public class ProcessListeners extends AbstractDispatcherCallback implements
        ApplicationContextAware {

    List<ProcessListener> listeners;
    
    ThreadLocal<ProcessEvent> SYNCH_PROCESS_EVENT = new ThreadLocal<ProcessEvent>();

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        listeners = GeoServerExtensions.extensions(ProcessListener.class, context);
    }

    public void fireProcessSubmitted(ProcessEvent event) {
        // direct process execution? Save the event for later usage
        if(!event.isAsynchronous() && !event.isChained()) {
            SYNCH_PROCESS_EVENT.set(event.clone());
        }
        
        for (ProcessListener pl : listeners) {
            pl.processSubmitted(event);
        }
    }

    public void fireProcessCancelled(ProcessEvent event) {
        for (ProcessListener pl : listeners) {
            pl.processCancelled(event);
        }
    }

    public void fireProcessExecuted(ProcessEvent event) {
        // direct process execution? Save the event for later usage
        if(!event.isAsynchronous() && !event.isChained()) {
            SYNCH_PROCESS_EVENT.set(event.clone());
        }
        
        for (ProcessListener pl : listeners) {
            pl.processExecuted(event);
        }
    }

    public void fireProcessCompleted(ProcessEvent event) {
        for (ProcessListener pl : listeners) {
            pl.processExecuted(event);
        }
    }
    
    public void fireProcessFailed(ProcessEvent event) {
        // remove the thread local in case we failed before encoding
        if(!event.isAsynchronous() && !event.isChained()) {
            SYNCH_PROCESS_EVENT.remove();
        }

        for (ProcessListener pl : listeners) {
            pl.processFailed(event);
        }
    }
    
    @Override
    public void finished(Request request) {
        ProcessEvent event = SYNCH_PROCESS_EVENT.get();
        if(event != null) {
            // clean up
            SYNCH_PROCESS_EVENT.remove();
            
            /// notify
            if(request.getError() != null) {
                event.error = request.getError();
                fireProcessFailed(event);
            } else {
                fireProcessCompleted(event);
            }
        }
    }
    
}
