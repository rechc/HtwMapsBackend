package de.htwmaps.algorithm;

import java.util.LinkedList;

import de.htwmaps.util.FibonacciHeap;

/*
 * @author Stanislaw Tartakowski
 * 
 * This is a concurrent implementation of an graph search algorithm based on Dijkstra's.
 * Depart from classic implementations this algorithm has a goal oriented heuristic similar to A*'s 
 * and is optimized for maximal speed performance. Though, this algorithm doesn't guarantee
 * best possible solution, but a relatively good one. This class can only be reasonably used if the caller of this class
 * remains sleeping until this class awakens him when the work is done.
 */
public class Dijkstra extends Thread {
	private static boolean finnished;
	private static int count;
	private boolean thread1;
	private FibonacciHeap Q;
	private DijkstraNode startNode, endNode;
	private Object caller;
	private String name;
	
	public Dijkstra(FibonacciHeap Q, DijkstraNode startNode, DijkstraNode endNode, boolean thread1, Object caller, String name) {
		this.Q = Q;
		this.startNode = startNode;
		this.endNode = endNode;
		this.thread1 = thread1;
		this.caller = caller;
		this.name = name;
	}
	
	@Override
	public void run() {
		try {
			dijkstra();
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
			synchronized (getClass()) { count++; } 							
			if (count == 2) {
				reactivateCaller();
			}
			return;
		}
	}
	
	/*
	 * Main loop
	 */
	private void dijkstra() throws InterruptedException {
		startNode.setDist(0.0);
		touch(startNode);
		Q.decreaseKey(startNode, 0.0);
		mainloop:while (Q.size() > 0) {
			if (finnished) {
				throw new InterruptedException(this + " has been finnished");
			}
			DijkstraNode currentNode = (DijkstraNode) Q.popMin();
			if (currentNode == null || currentNode.getDist() == Double.MAX_VALUE || currentNode == endNode ) {
				if (currentNode == endNode && currentNode.getPredecessor() != null) {
					finnished = true;
					break;					
				} else {
					throw new InterruptedException(this + " no path found");
				}
			}
			currentNode.setRemovedFromQ(true);
			LinkedList<Edge> edges = currentNode.getEdgeList();
			for (Edge edge : edges) {
				DijkstraNode successor = (DijkstraNode) edge.getSuccessor();
				if (thread1 || (!thread1 && !edge.isOneway())) {
					synchronized(getClass()) {
						if (checkForCommonNode(currentNode, successor)) {
							break mainloop;
						}
						if (!successor.isRemovedFromQ()) {
							updateSuccessorDistance(currentNode, edge);
							Q.decreaseKey(successor, successor.getDist());
						}
					}
				}
			}
		}
		reactivateCaller();
	}

	private void touch(DijkstraNode node) {
		if (thread1) {
			node.setTouchedByTh1(true);
		} else {
			node.setTouchedByTh2(true);
		}
	}

	private boolean checkForCommonNode(DijkstraNode currentNode, DijkstraNode successor) {
		if (!thread1 && successor.isTouchedByTh1() || thread1 && successor.isTouchedByTh2()) {
			concantenate(currentNode, successor);
			return true;
		}
		return false;
	}

	private void concantenate(DijkstraNode currentNode, DijkstraNode successor) {
		DijkstraNode tmp;
		if (!finnished) {
			finnished = true;
			while (successor != null) {
				tmp = successor.getPredecessor();
				successor.setPredecessor(currentNode);
				currentNode = successor;
				successor = tmp;
			}
		}
	}

	private void updateSuccessorDistance(DijkstraNode currentNode, Edge edge) {
		DijkstraNode successor = (DijkstraNode)edge.getSuccessor();
		double alternative = currentNode.getDist() + edge.getDistance() - currentNode.getDistanceTo(endNode) + successor.getDistanceTo(endNode);
		if (alternative < successor.getDist()) {
			successor.setDist(alternative);
			successor.setPredecessor(currentNode);
			touch(successor);
		}
	}
	
	private void reactivateCaller() {
		synchronized(caller.getClass()) {							
			caller.getClass().notifyAll();
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
}