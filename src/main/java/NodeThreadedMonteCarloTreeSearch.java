import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class NodeThreadedMonteCarloTreeSearch extends MonteCarloTreeSearch {
	public NodeThreadedMonteCarloTreeSearch(StateMachine machine, Role role) {
		super(machine, role);
	}

	private volatile boolean shouldStop = false;
	private volatile boolean debug = false;
	private volatile MCNode root = null;
	private static final int numThreads = 1;

    class Timeout extends Thread {
    	private static final int buffer = 3000;
        long timeout;
        Timeout(long timeout) {
            this.timeout = timeout;
        }

        @Override
		public void run() {
        	while (timeout - System.currentTimeMillis() > buffer) {
	        	try {
					Thread.sleep(timeout - System.currentTimeMillis() - buffer);
				} catch (InterruptedException e) {

				}
        	}
        	System.out.println("Stopping");
        	shouldStop = true;
        }
    }

    class ExpandTree extends Thread {
        ExpandTree() {
        }

        @Override
		public void run() {
        	while (!shouldStop) {
	        	try {
					expandTree();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// e.printStackTrace(); // lol
				}
        	}
        }
    }

    class MCNode {
    	// If move is not null, then we only consider children in which we make that move.
    	// If move is null, there will be one child for each move we can make
    	Move move = null;
    	int visits = 0;
    	double utility = 0;
    	MCNode parent = null;
    	MachineState state;
    	ArrayList<MCNode> children;
    	Semaphore semaphore;
    	public MCNode(MachineState state, int visits, double utility, MCNode parent, ArrayList<MCNode> children, Move move) {
    		this.move = move;
    		this.state = state;
    		this.visits = visits;
    		this.parent = parent;
    		this.utility = utility;
    		this.children = children;
    		semaphore = new Semaphore(1);
    	}
    }

    double selectfn(MCNode node) {
    	//System.out.println("1 " + node.utility/node.visits + " 2 " + Math.log(node.parent.visits)/node.visits);
    	return (new Random().nextFloat() * (20 + node.utility/node.visits)+ new Random().nextFloat() * 20 * Math.sqrt(2*Math.log(node.parent.visits)/(node.visits)));
    }

    public MCNode select (MCNode node, int depth, int[] numContinues, int num) {
    	/*if (depth == 1 && !node.semaphore.tryAcquire()) {
    		return null;
    	}*/
    	boolean settle = numContinues[0] > num;
    	if ((node.visits == 0 && node.parent == null) || (settle && depth > 1 && node.move == null)) {
    		return node;
    	}
    	if (node.utility < 0) {
    		System.err.println("Utility is " +  node.utility);
    	}
    	if (depth > 495) {
    		System.out.println(node.state + " " + node.move + " " + node.utility + " " + node.utility);
    	}
    	if (depth > 500) {
    		System.out.println("Depth limit");
    		numContinues[0]++;
    		return null;
    	}
    	if (node.visits==0 && node.move == null) {
    		return node;
    	}
    	if (super.stateMachine.isTerminal(node.state)) {
    		numContinues[0]++;
    		return null;
    	}
	    for (int i=0; i<node.children.size(); i++) {
	    	MCNode child = node.children.get(i);
	    	 if (child.visits==0) {
	    		 return select(child, depth + 1, numContinues, num);
	    	 }
	     }
	     double score = -1;
	     MCNode result = node;
	     if (node.children.size() == 0) {
	    	 numContinues[0]++;
	    	 return null;
	     }
	     for (int i=0; i<node.children.size(); i++) {
	    	 double newscore = selectfn(node.children.get(i));
	    	  if (depth > 495) {
	    		  System.out.println("new score " + newscore + " old score " + score + " node " + node.children.get(i).state);
	    	  }
	          if (newscore>score) {
	        	  score = newscore;
	        	  result=node.children.get(i);
	          }
	     }

	     if (result == node || score < 0) {
	    	 numContinues[0]++;
	    	 return null;
	     }
	     return select(result, depth + 1, numContinues, num);
     }

    public boolean expand (MCNode node) throws MoveDefinitionException, TransitionDefinitionException {
    	if (super.stateMachine.isTerminal(node.state)) {
    		return true;
    	}
    	List<Move> actions = super.stateMachine.getLegalMoves(node.state, super.role);
	    for (int i=0; i < actions.size(); i++) {
	    	addNewStates(node, actions.get(i));
	    };
	    return true;
    }

    public void addNewStates(MCNode node, Move move) throws MoveDefinitionException, TransitionDefinitionException {
    	if (node.move != null) {
    		throw new MoveDefinitionException(node.state, super.role);
    	}
    	List<List<Move>> moves = super.stateMachine.getLegalJointMoves(node.state, super.role, move);
    	MCNode newnode = new MCNode(node.state, 0, 0, node, new ArrayList<MCNode>(), move);
    	node.children.add(newnode);
    	for (List<Move> jointMove: moves) {
    		MachineState state = super.stateMachine.getNextState(node.state, jointMove);
    		//System.out.println("Made move " + move + " from " + node.state + " to " + state);
    		newnode.children.add(new MCNode(state, 0, 0, newnode, new ArrayList<MCNode>(), null));
    	}
    }

    public double minMax(MCNode node, int depth) {
    	if (depth == 0) {
    		return node.utility;
    	}
    	double minVal = 101;
    	for (MCNode child: node.children) {
    		for (MCNode grandChild: child.children) {
    			minVal = Math.min(minMax(grandChild, depth - 1), minVal);
    		}
    	}
    	if (minVal == 101) {
    		return node.utility;
    	}
    	return minVal;
    }

    public boolean backpropagateH(MCNode node, double score, MCNode base, int depth) {
	    if (score < 0) {
	            System.err.println("wtf");
	    }
	    node.visits = node.visits+1;
        if (node.move != null) {
                node.utility = node.utility+score;
        } else {
            double min = 101;
            for (MCNode child: node.children) {
                    min = Math.min(minMax(child, 1), min);
            }
            if (min == 101) {
                    min = node.utility;
                    if (new Random().nextFloat() < 0.01) {
                            System.out.println("bad backprop");
                    }
            }
            node.utility = min;
        }
        if (node.parent != null) {
            backpropagateH(node.parent,score, node, depth + 1);
        }
        return true;
    }

    public boolean backpropagate (MCNode node, double score) {
    	return backpropagateH(node.parent, score, node, 1);
	}

    private void expandTree() throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		int num = 0;
		int numBad = 0;
		int[] numContinues = new int[1];
		numContinues[0] = 0;
		while (!shouldStop) {
			//boolean settle = numContinues > num;
			MCNode s = select(root, 0, numContinues, num);
			if (s == null) {
				continue;
			}
			expand(s);
			int[] theDepth = new int[1];
			MachineState terminal = super.stateMachine.performDepthCharge(s.state, theDepth);
			double score = 0;
			if (terminal != null) {
				score = (double) super.stateMachine.getGoal(terminal, super.role);
			} else {
				System.out.println("hmm, terminal state is null");
			}
			//System.out.println("Score " + score + " state " + terminal);
			backpropagate(s, score);
			releaseLocks(s);
			num++;
			if (num % 5000 == 0) {
				System.out.println("Performed " + num + " depth charges " + numBad + " errors, got score " + score);
			}
		}
		//System.out.println("Stopped");
    }

	private void releaseLocks(MCNode s) {
		/*
		// TODO Auto-generated method stub
		if (s == null) {
			return;
		}
		if (s.parent != null && s.parent.parent == null) {
			if (s.semaphore.availablePermits() != 0) {
				System.out.println("Bad permit");
			} else {
				s.semaphore.release();
			}
		}
		releaseLocks(s.parent);*/
	}


	private void testDepth(MCNode n, int depth, int[] numProcessed, int[] numPositive, int[] sumPositive, int[] minDepth, int[] maxDepth, int[] totalDepth) {
		final int printDepth = 3;
		if (debug) {
			if (n.children.size() == 0) {
				minDepth[0] = Math.min(minDepth[0], depth);
				maxDepth[0] = Math.max(maxDepth[0], depth);
				totalDepth[0] += depth;
			}
			if (numProcessed[0] > 10000) {
				System.out.println("Warning: node overflow");
				return;
			}
			numProcessed[0]++;

			if (depth == printDepth) {
				if (new Random().nextFloat() < 1) {
					System.out.println("Node utility " + n.utility / n.visits + " visits " + n.visits + " depth " + depth);
				}

				if (n.visits > 0) {
					numPositive[0]++;
					sumPositive[0] += n.visits;
				}
			}
			for (MCNode child: n.children) {
				testDepth(child, depth + 1, numProcessed, numPositive, sumPositive, minDepth, maxDepth, totalDepth);
			}
		}
	}

	@Override
	public void search(Duration searchTime) {
		long start = System.currentTimeMillis();
		shouldStop = false;
		Timeout t = new Timeout(Instant.now().toEpochMilli() + searchTime.toMillis());
		t.start();
		root = new MCNode(this.root.state, 0, 0, null, new ArrayList<MCNode>(), null);
		try {
            expand(root);
		} catch(Exception e) {}
		root.visits++;

		for (int i = 0; i < numThreads; i++) {
			ExpandTree et = new ExpandTree();
			et.start();
			if (i == 0) {
				try { // First thread gets head start
					Thread.sleep(300);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		while (!shouldStop) {
			try {
				expandTree();
				Thread.sleep(10);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		int[] numProcessed = new int[1];
		int[] numPositive = new int[1];
		int[] sumPositive = new int[1];
		int[] minDepth = new int[1];
		int[] maxDepth = new int[1];
		int[] totalDepth = new int[1];
		minDepth[0] = 100000;
		testDepth(root, 0, numProcessed, numPositive, sumPositive, minDepth, maxDepth, totalDepth);
		System.out.println(numProcessed[0] + " processed, " + numPositive[0] + " nodes visited, " + sumPositive[0] + " total visits");
		System.out.println(minDepth[0] + " mindepth, " + maxDepth[0] + " maxDepth, " + ((float) totalDepth[0]) / numProcessed[0] + " totaldepth");
	}

	@Override
	public Move chooseMove() {


		double bestScore = -1;
		Move bestMove = null;
		for (int i = 0; i < root.children.size(); i++) {
			MCNode n = root.children.get(i);
			System.out.println("Considering node, visited " + n.visits + " times, move " + n.move + ", value " + n.utility / n.visits + ", num children " + n.children.size());
			if ((n.utility / n.visits) > bestScore) {
				bestScore = (n.utility / n.visits);
				bestMove = n.move;
			}
		}

		System.out.println("Utility: " + bestScore);
		return bestMove;
	}
}