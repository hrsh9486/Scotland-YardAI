package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static java.lang.Math.max;
import static java.lang.Math.min;


public class MyAi implements Ai {
	ArrayList<ArrayList<Integer>> distances;
	ArrayList<Player> detectives;
	Player mrX;

	// Returns a matrix storing the shortest path from every node to every other node, using
	// Floyd Warshall Algorithm
	public void  makeDistancesFW(Board board) {
		ImmutableValueGraph graph = board.getSetup().graph;
		ArrayList<ArrayList<Integer>> distances = new ArrayList<>();

		// Sets initial values as infinity or 0
//		System.out.println(graph.nodes().size());
		for (Object node1: graph.nodes()) {
			distances.add(new ArrayList<>());
			for (Object node2 : graph.nodes()) {
				Integer node1Num = (Integer) node1;
				Integer node2Num = (Integer) node2;
				if (node1 == node2) {
					distances.get(node1Num - 1).add(node2Num - 1, 0);
				}
				else if (graph.hasEdgeConnecting(node1, node2)) {
					distances.get(node1Num - 1).add(node2Num - 1, 1);
				}
				else {
					distances.get(node1Num - 1 ).add(node2Num - 1, 9999);
				}
			}
		}

		for( Integer k= 0; k<distances.size(); k++ ){
			for( Integer i= 0; i<distances.size(); i++ ){
				for( Integer j= 0; j<distances.size(); j++ ){
					if(distances.get(i).get(j) > distances.get(i).get(k) + distances.get(k).get(j) ){
						distances.get(i).set(j,(distances.get(i).get(k) + distances.get(k).get(j)));
					}
				}
			}
		}
		this.distances =  distances;
	}

	@Nonnull @Override public String name() { return "Doofenshmirtz"; }


	public Pair<Move, Integer> minimax(Board.GameState gs, Move move, Integer depth, boolean mrXTurn ){
		// No DOUBLE remaining. why?
		// See the tickets used and tickets available by this game state and the following one

		//Create a copy of game state so that we can run advance on it, while also retaining original state.
		Board.GameState copyState	=  constructGameState(gs);

		//We need to reassess this move.source() thing. Aside from that, miniMax works.
		if (depth == 0){
			return new Pair<> (move, score(gs,move.source()));
		}

		if (depth == 7){
			Integer maxEval = -9999;
			Pair<Move, Integer> bestEval = new Pair<>(move,maxEval);
			for (Move m: gs.getAvailableMoves()){
				Pair<Move, Integer> eval = minimax(copyState.advance(m), m, depth-1, false);
				if (eval.right() > bestEval.right()) {
					bestEval = eval;
				}
				copyState = constructGameState(gs);
			}
			System.out.println("Mrx Turn finished");
			return bestEval;
		}

		else {
			Integer minEval = 9999;
			Pair<Move, Integer> bestEval = new Pair<>(move,  minEval);
			for (Move m: gs.getAvailableMoves()){
				Pair<Move, Integer> eval = minimax(gs.advance(m), m, depth-1, true);
				if (eval.right()< bestEval.right()){
					bestEval = eval;
				}
				copyState = constructGameState(gs);
			}
			return bestEval;
		}
	}


	public Integer score(Board.GameState gameState, Integer destination) {
		Integer maxDistance = 0;
		for (Player p : detectives) {
			maxDistance = max(maxDistance, distances.get(destination).get(p.location()));
		}
		return maxDistance;
	}

	public void createPlayers(Board board) {
		ImmutableSet<Piece> pieces = board.getPlayers();
		ArrayList<Player> detectives = new ArrayList<>();

		for (Piece piece : pieces) {
			if (piece.isDetective()){
				// For types of tickets, call .getCount() and add to an immutable map
				HashMap<ScotlandYard.Ticket, Integer> playerTicketCount = new HashMap<>();
				for (ScotlandYard.Ticket ticket : ScotlandYard.DETECTIVE_TICKETS ) {
					playerTicketCount.put(ticket, board.getPlayerTickets(piece).get().getCount(ticket));;
				}

				Integer location  = board.getDetectiveLocation((Piece.Detective) piece).orElse(-1);
				detectives.add(new Player(piece, ImmutableMap.copyOf(playerTicketCount), location));
			}
			else if (piece.isMrX()) {
				HashMap<ScotlandYard.Ticket, Integer> mrXTicketCount = new HashMap<>();
				for (ScotlandYard.Ticket ticket : ScotlandYard.MRX_TICKETS ) {
					mrXTicketCount.put(ticket, board.getPlayerTickets(piece).get().getCount(ticket));;
				}
				Integer location  = board.getAvailableMoves().asList().get(0).source();
				Player mrX = new Player(piece,ImmutableMap.copyOf(mrXTicketCount), location );
				this.mrX = mrX;

			}
		}
		// We shouldn't be making attributes for mr x and detectives, just return a pair.
		this.detectives = detectives;
	}

	public Board.GameState constructGameState (Board board){
		PseudoGameStateFactory factory = new PseudoGameStateFactory();
		createPlayers(board);
		Player mrX = this.mrX;
		List detectives = this.detectives;
		ImmutableList<LogEntry> log = board.getMrXTravelLog();
		Board.GameState newState = factory.build(board.getSetup(), mrX, ImmutableList.copyOf(detectives));
		return newState;
	}


	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		makeDistancesFW(board);

		// Initialise a copy game state, which we can use to track positions during minimax
		Board.GameState gameStateAtStartOfMove = constructGameState(board);
		Pair<Move, Integer> bestMiniMax = new Pair (gameStateAtStartOfMove.getAvailableMoves().asList().get(0), 0);

		// Iterate through possible moves and run minimax, store move sequence with the best possible move.
		for (Move move: gameStateAtStartOfMove.getAvailableMoves()) {
			// Depth used to be 7
			Pair <Move, Integer> currentMiniMax = minimax(gameStateAtStartOfMove, move, 7, true);
			if (currentMiniMax.right() > bestMiniMax.right()) {
				bestMiniMax = currentMiniMax;
			}
			gameStateAtStartOfMove = constructGameState(board);
		}

		return bestMiniMax.left();

//		var moves = board.getAvailableMoves().asList();
//		return moves.get(new Random().nextInt(moves.size()));
	}
}

// Our overall structure is good. To make this work, we need to address 3 things
// 1. Our base case in Minimax evaluates score based on the relative position of all players to some detective.
//	  We need to change this to be Mr X's position
// 2. The pseudogamestate we're passing into minimax always has an empty log and its remaining always only has Mr X in it.
//    We need to find a wat to access both of these attributes from board, and mirror their updates in our pseudo game state.
// 3. Addressing the issue above, our current pseudo game state is encapsulated by the factory method, so we need to rewrite
//    that class to get rid of the factory and instead just have a public pseudo game state.