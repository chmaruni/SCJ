package hedc.scj.hedc;
/*
 * Copyright (C) 1998 by ETHZ/INF/CS
 * All rights reserved
 * 
 * @version $Id: MetaSearchImpl.java 3342 2003-07-31 09:36:46Z praun $
 * @author Christoph von Praun
 */

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.*;

import scj.Task;
import hedc.ethz.util.*;

/**
 * An object of type RequestDispatchServlet is the entry point for http requests to the HEDC system. 
 * The communication with the WWW server is done through the Servlet API.
 */
public class MetaSearchImpl implements MetaSearch {

    private static final String TFA01_ = "TFA01 - failed to parse date from string '%1'";
    private static final String TFA02_ = "TFA02 - failed to create uniqueInstance (%1)";
    private static final int MSR_DEFAULT_DURATION_ = 5000;
    private static final int MSR_MAX_THREADS_ = 50;
    private static String MSR_TEMPLATE_LOCATION_ = null;
    private static String MSR_FRAME_TEMPLATE_ = "hedc_synoptic_frame";
    private static char[] MSR_ROW_HEADER_TEMPLATE_ = null;
    private static char[] MSR_ROW_LINE_TEMPLATE_ = null;
    private static char[] MSR_ROW_EMPTY_LINE_TEMPLATE_ = null;
    private static MetaSearchImpl uniqueInstance_ = null;
    private TaskFactory taskFac_ = null;
    
    public static MetaSearchImpl getUniqueInstance() {
	if (uniqueInstance_ == null)
	    try {
		uniqueInstance_ = new MetaSearchImpl();
	    } catch (Exception e) {
		Messages.error(TFA02_, e);
	    }
	return uniqueInstance_;
    }

    private MetaSearchImpl() {
	taskFac_ = new TaskFactory();
	
	MSR_ROW_HEADER_TEMPLATE_ = FormFiller.internalize("hedc_synoptic_row_header");
	MSR_ROW_LINE_TEMPLATE_ = FormFiller.internalize("hedc_synoptic_row_body");
	MSR_ROW_EMPTY_LINE_TEMPLATE_ = FormFiller.internalize("hedc_synoptic_row_empty_body");
    }

    public void scjTask_search(Task<Long> now, Task<?> later, Hashtable h, Writer wrt, MetaSearchRequest r) throws IOException {
    	Task<List> searchTask = new Task<List>();
    	
    	Task<List<HedcTask>> resultsTask = new Task<List<HedcTask>>();
    	    	
    	Task<Void> joinTask = new Task<Void>();
    	this.scjTask_search_2(joinTask, now, resultsTask, wrt);
    	
    	this.scjTask_search(resultsTask, joinTask, h, r);
    	
    	resultsTask.hb(joinTask);
    	joinTask.hb(later);
    	
    }
    
    public void scjTask_search_2(Task<Void> now, Task<Long> result, Task<List<HedcTask>> results, Writer wrt) throws IOException {
    	System.out.println("executing search_2");
    	long res = writeResults_(results.result(), wrt);
    	result.setResult(res);
    }

    List<HedcTask> taskList;
    public void scjTask_search(Task<List<HedcTask>> now, Task<?> later, Hashtable h, MetaSearchRequest r) {
	// create tasks
	taskList = null;
	String dateString = (String) h.get("DATETIME");
	Date date = null;
	
	if (dateString != null) 
	    try {
		Messages.debug(1, "MetaSearchImpl::search before parse");
		date = RandomDate.parse(dateString);
		Messages.debug(1, "MetaSearchImpl::search after parse date=%1", date);
	    } catch (Exception e) {
		Messages.error(TFA01_, dateString);
	    }
	else
	    Messages.error(TFA01_, dateString);
	
	System.out.println("scheduling search_3");
	Task<List<HedcTask>> joinTask = new Task<List<HedcTask>>();
	this.scjTask_search_3(joinTask, h);
	
	if (date != null) {
	    Thread t = Thread.currentThread();
	    taskList = taskFac_.makeTasks(h, date, r);
	    // take precaution that the issueing Thread is interrupted when all tasks are done
	    r.registerInterrupt(t, taskList.size());
	    for (Iterator<HedcTask> e = taskList.iterator(); e.hasNext(); ) {
	    	System.out.println("scheduling another task");
	    	Task<Void> taskTask = new Task<Void>();
	    	e.next().scjTask_run(taskTask);
	    	taskTask.hb(joinTask);	    	
	    }
	}
	
	joinTask.hb(later);
    }

    public void scjTask_search_3(Task<List<HedcTask>> now, Hashtable h) {    	
    	long waitTime = MSR_DEFAULT_DURATION_;
    	System.out.println("executing search_3... waiting " + waitTime * 1000);
    	try {
    	    waitTime = Long.valueOf((String)h.get("WAIT_TIME")).longValue() * 1000;
    	} catch (Exception e) {}
    	try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e1) {
			//e1.printStackTrace();
		}
		System.out.println("continuing search_3 " + waitTime * 1000);
    	// invalidate all tasks and interrupt the corresponding threads
	    for (Iterator<HedcTask> e = taskList.iterator(); e.hasNext(); )
	    	e.next().cancel();
	    
	    now.setResult(taskList);	    
    }

    /**
     * Input is a list of tasks
     */
    private long writeResults_(List l, Writer w) throws IOException {
	long ret = -1;
	StringWriter sw = new StringWriter();
	Hashtable h = new Hashtable();
	for (Iterator e = l.iterator(); e.hasNext(); ) {
	    MetaSearchResult r = (MetaSearchResult) e.next();
	    Iterator i = r.getInfo();
	    if (i.hasNext()) {
		 h = (Hashtable) i.next();
		 if (h.get("URL") != null) {
		     do {
			 Generator.generate(sw, h, MSR_ROW_LINE_TEMPLATE_);
		     } while (i.hasNext() && (h = (Hashtable) i.next()) != null);
		 } else
		     Generator.generate(sw, h, MSR_ROW_EMPTY_LINE_TEMPLATE_);
	    }
	}

	h.put("ROWS", sw.getBuffer().toString()); 
	FormFiller f = new FormFiller(w, h, MSR_FRAME_TEMPLATE_);
	f.fillForm();
	return ret;
    }

    private final void printResults_(List l) {
	for (Iterator e = l.iterator(); e.hasNext(); ) {
	    MetaSearchResult t = (MetaSearchResult) e.next();
	    System.out.println(t.getInfo());
	    System.out.println(t.results);
	}
    }

    private static final String RDI01_ = "RDI01 - an error occurred while opening your session (%1)";
    private static final String RDI02_ = "RDI02 - no session with ID %1 found";
    private static final String RDI03_ = "RDI03 - the RequestDispatch service is implicitly started through the server RequestDispatchServlet";
    private static final String RDI04_ = "RDI04 - session id %1 was not opened through this API";
}
