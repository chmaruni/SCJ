package hedc.scj.hedc;
/*
 * Copyright (C) 1998 by ETHZ/INF/CS
 * All rights reserved
 *
 * @version $Id: MetaSearchResult.java 3342 2003-07-31 09:36:46Z praun $
 * @author Christoph von Praun 
 */

import java.util.*;

import scj.Task;
import hedc.ethz.util.*;

public abstract class MetaSearchResult extends HedcTask implements Cloneable {
    public Date date = null;
    public List results = null;
    public MetaSearchRequest request = null;
    private boolean completed_ = false;
    
    protected abstract class MetaSearchResultIterator implements Iterator {
	protected Hashtable h_ = new Hashtable();
	protected Iterator resultIterator_ = null;
	protected boolean firstSeen_ = false;
	
	protected MetaSearchResultIterator () {
	    resultIterator_ = (results == null || results.size() == 0) ? null : results.iterator();
	}

	public boolean hasNext() {
	    boolean ret = false;
	    if (resultIterator_ != null) 
		ret = resultIterator_.hasNext();
	    else if (firstSeen_ == false) {
		if (!completed_) // item was canceled
		    h_.put("MESSAGE", "timed out");
		else 
		    h_.put("MESSAGE", "no results");
		firstSeen_= ret = true;
	    }
	    return ret;
	}
	
	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }

    public abstract Iterator getInfo();

    public synchronized void cancel() {	
	request = null;
	valid = false;
    }

    @Override
    public void scjTask_run(Task<Void> now) {
    	System.out.println("task running");
    	try {
    	    runImpl();
    	} catch(Exception e) {
    	    Messages.warn(-1, "Task::run exception=%1", e);
    	    // e.printStackTrace();
    	}
    	
	completed_ = true;
	//request is set to null when the thread is cancelled
	if(request != null) {
		request.countDownInterrupt();
	}
    }

    public static MetaSearchResult cloneTask(MetaSearchResult t) {
	MetaSearchResult ret = null;
	if (t != null && t instanceof HedcTask) 
	    try { 
		ret = (MetaSearchResult) t.clone();
		ret.results = null;
		ret.request = null;
		ret.completed_ = false;
		ret.date = null;
	    } catch (CloneNotSupportedException e) {
		Messages.error("MetaSearchResult::CloneNotSupported exception=%1", e);
	    }
	return ret;
    }
    
    public String toString() {
	StringBuffer sb = new StringBuffer();
	sb.append("##");
	for (Iterator it = getInfo(); it.hasNext(); ) {
	    Hashtable h = (Hashtable) it.next();
	    sb.append(h);
	    sb.append("#");
	}
	sb.append("#");
	return sb.toString();
    }
}







