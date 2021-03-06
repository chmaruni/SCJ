package scj.compiler.analysis.schedule.extraction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import scj.compiler.wala.util.SimpleGraph;

import com.ibm.wala.ssa.ISSABasicBlock;

public final class JoinNodeFlowData extends NormalNodeFlowData {

	private static final boolean DEBUG = false;
	
	private final EdgeFlowData[] incoming;
	
	JoinNodeFlowData(ISSABasicBlock basicBlock, int numIncomingEdges) {
		super(basicBlock);
		//this constructor is called when the initial flow data instances are created; so elements in incoming[] can be null before the meet operation ran
		incoming = new EdgeFlowData[numIncomingEdges];
	}
	
	void initAndMergeFromIncoming(EdgeFlowData[] incoming) {
		this.initEmpty();
		
		assert this.incoming.length == incoming.length;
		
		for(int i = 0; i < incoming.length; i++) {
			this.incoming[i] = incoming[i];
		}
		
		//we do not create a new happensBeforeMap because we have to do the intersection of all
		//and for that we need the null as a flag of "nothing happened yet"		
		if(DEBUG)
			System.out.println("JoinNodeFlowData: joining " + basicBlock);
		
		//first merge all the data from non back edges
		for(int i = 0; i < incoming.length; i++) {
			EdgeFlowData edge = incoming[i];
			assert edge != null; //should not happen after we ran the meet operator
			
			if( ! (edge instanceof BackEdgeFlowData)) {
				assert ! edge.isInitial() : "guess that shouldn't happen, but if it wouldn't be a problem either...";
				this.mergeState(edge.getData());
			}			
		}
		
		//now we contain all the variables that have been valid before the loop;
		//forward their values to the new iteration by fixing their loop contexts
		//we pretend those guys are phi variables
		//at the same time we fix our currentLoopContexts
		for(int i = 0; i < incoming.length; i++) {
			EdgeFlowData edge = incoming[i];
			if((edge instanceof BackEdgeFlowData) && ! edge.isInitial()) {
				BackEdgeFlowData backEdge = (BackEdgeFlowData)edge;
				for(TaskVariable tv : this.partialSchedule()) {
					this.addPhiVariable(new PhiVariable(tv.loopContext.contextByAddingLoop(backEdge), tv.ssaVariable), tv);
				}
				
				//add the back edge to our set of loop contexts
				Set<LoopContext> newContexts = new HashSet<LoopContext>();
				for(LoopContext lc : this.currentLoopContexts()) {
					newContexts.add(lc.contextByAddingLoop(backEdge));
				}
				this.addAllCurrentLoopContexts(newContexts);
			}			
		}
		
		//now merge the backe dges
		for(int i = 0; i < incoming.length; i++) {
			EdgeFlowData edge = incoming[i];
			if((edge instanceof BackEdgeFlowData) && ! edge.isInitial()) {
				this.mergeState(edge.getData());
			}			
		}
		
		//now we unioned all edges; check that for all edges if an incoming data "knows" about lhs and rhs, it also knows the edge;
		//otherwise they disagree and we can't keep the edge
		
		for(int i = 0; i < this.incoming.length; i++) {
			EdgeFlowData edge = this.incoming[i];			
			this.filterUnreliableEdges(edge);			
		}
	}
	
	@Override
	JoinNodeFlowData duplicate(ISSABasicBlock forBasicBlock) {
		JoinNodeFlowData data = new JoinNodeFlowData(forBasicBlock, incoming.length);
		data.copyState(this);
		return data;
	}
	
	@Override
	public NormalNodeVisitor createNodeVisitor(TaskScheduleSolver solver) {
		return new JoinNodeVisitor(solver, this);
	}
	
	EdgeFlowData incomingEdgeAtPosition(int pos) {
		return incoming[pos];
	}
	
	NormalNodeFlowData incomingDataAtPosition(int pos) {
		EdgeFlowData edge = incoming[pos];
		if(edge != null) {
			NormalNodeFlowData data = edge.getData();
			assert ! data.isInitial();
			return data;
		} else {
			return null;
		}
	}
	
	void addPhiVariable(PhiVariable phi, TaskVariable task) {
		if (phiMappings == null)
			phiMappings = new HashMap<PhiVariable, Set<TaskVariable>>();
		
		Set<TaskVariable> tasks = phiMappings.get(phi);
		if(tasks == null) {
			tasks = new HashSet<TaskVariable>();
			phiMappings.put(phi, tasks);
			
		}
		tasks.add(task);		
	}
		
	@Override
	boolean stateEquals(FlowData otherData) {
		return super.stateEquals(otherData);
	}
	
	protected void filterUnreliableEdges(EdgeFlowData edge) { //iterate a copy of my hb edges and check whether the incoming data agrees
		if(!edge.isInitial()) {
			NormalNodeFlowData other = edge.getData();
			SimpleGraph<TaskVariable> partialSchedule = this.partialSchedule();
			Iterator<TaskVariable> lhsNodes = partialSchedule.iterator();
			while(lhsNodes.hasNext()) {
				TaskVariable lhs = lhsNodes.next();
				
				Iterator<TaskVariable> rhsNodes = partialSchedule.getSuccNodes(lhs);
				while(rhsNodes.hasNext()) {
					TaskVariable rhs = rhsNodes.next();
					SimpleGraph<TaskVariable> otherSchedule = other.partialSchedule();
					//check for each edge whether the other guy agrees on a) the existence of the task variables and b) on the edge lhs->rhs
					if(otherSchedule.containsNode(lhs) && otherSchedule.containsNode(rhs)) {
						if(! otherSchedule.hasEdge(lhs, rhs))
							//not sure if this throws an concurrent modification exception
							partialSchedule.removeEdge(lhs, rhs);
					}
				}
			}			
		}
	}

	@Override
	public void copyState(FlowData v) {
		super.copyState(v);
		assert(v instanceof JoinNodeFlowData);
		JoinNodeFlowData other = (JoinNodeFlowData)v;
		assert other.incoming.length == incoming.length;
		
		for(int i = 0; i < incoming.length; i++) {
			incoming[i] = other.incoming[i];
		}
		
	}
	
	@Override
	public String toString() {
		return "Join-" + super.toString();
	}
}
