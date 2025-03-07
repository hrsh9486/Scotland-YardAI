package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
	
	// Optimisations:
	// xremoveDoubleMoves - gets rid of double moves from the available moves
	// removeNextToDetectiveMoves - gets rid of moves where mrX is placed next to a detective
	// prioritiseDoubleMoves - if available moves is 0, then add the double moves in
	// we can schedule/prioritise secret moves later
	
	public Pair<ArrayList<Move>, ArrayList<Move>> removeDoubleMoves(ImmutableSet<Move> moves) {
		ArrayList<Move> movesToRemove = new ArrayList<>(moves);
		ArrayList<Move> onlySingleMoves = new ArrayList<>();
		ArrayList<Move> onlyDoubleMoves = new ArrayList<>();
		for (Move m: movesToRemove){
			Iterator<ScotlandYard.Ticket> ticketIterator = m.tickets().iterator();
			List<ScotlandYard.Ticket> ticketList = Lists.newArrayList(ticketIterator);
			if (!ticketList.contains(ScotlandYard.Ticket.DOUBLE)) {
				onlySingleMoves.add(m);
			}
			else {
				onlyDoubleMoves.add(m);
			}
		}
		Pair<ArrayList<Move>, ArrayList<Move>> singleAndDoubleMoves= new Pair<>(onlySingleMoves,  onlyDoubleMoves);
		return singleAndDoubleMoves;
	}

	public Pair<Move, Integer> minimax(MirrorGameState gs, Move move, Integer alpha, Integer beta, Integer depth){

		if (depth == 0){
			return new Pair<Move, Integer>(move, score(gs, gs.getMrX().location()));
		}
		// This is for the mrX (the maximising player)
		else if (depth % 3 == 0){
			if (!gs.getWinner().isEmpty()){
				return new Pair<>(move, -9999);}

			Integer maxEval = -9999;

			Pair<ArrayList<Move>, ArrayList<Move>> singleAndDoubleMoves = removeDoubleMoves(gs.getAvailableMoves());
			ArrayList<Move> moves = singleAndDoubleMoves.left();
			ArrayList<Move> onlyDoubleMoves = singleAndDoubleMoves.right();
			Move bestMove = moves.get(0);
			for (Move newMove: moves){
				//make a copy
				MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
				Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
				if (currentEval.right()>maxEval){
					maxEval = currentEval.right();
					bestMove = newMove;
				}
				alpha = max(alpha, currentEval.right());
				if (beta<=alpha){break;}
			}
			return new Pair<Move, Integer> (bestMove,maxEval);
		}

		// This is for the detectives (the minimising players)
		else {
			if (!gs.getWinner().isEmpty()){
				return new Pair<>(move, 9999);}
			Integer minEval = 9999;

			Pair<ArrayList<Move>, ArrayList<Move>> singleAndDoubleMoves = removeDoubleMoves(gs.getAvailableMoves());
			ArrayList<Move> moves = singleAndDoubleMoves.left();
			ArrayList<Move> onlyDoubleMoves = singleAndDoubleMoves.right();
			Move bestMove = moves.get(0);

			for (Move newMove: moves){
				//make a c() opy
				MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
				Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
				if (currentEval.right() < minEval){
					minEval = currentEval.right();
					bestMove = newMove;
				}
				beta = min(beta, currentEval.right());
				if (beta<=alpha){break;}
			}
			return new Pair<Move, Integer> (bestMove, minEval);
		}
	}

	

	public Integer score(Board.GameState gameState, Integer destination) {
		Integer minDistance = 9999;
		for (Player p : detectives) {
			minDistance = min(minDistance, distances.get(destination - 1 ).get(p.location()));
		}
		return minDistance;
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


	public MirrorGameState initialiseMirrorGameState(Board board){
		//MirrorGameStateFactory factory = new MirrorGameStateFactory();
		createPlayers(board);
		Player mrX = this.mrX;
		List detectives = this.detectives;
		ImmutableList<LogEntry> log = board.getMrXTravelLog();

		return new MirrorGameState(board.getSetup(), ImmutableSet.of(mrX.piece()), log, mrX, detectives );
	}
	

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		makeDistancesFW(board);

		int playersPlaying = board.getPlayers().size();
		int depth = playersPlaying * 1;
		depth = 3;
		// Build a new game state, preserve is a copy, my is to run minimax on
		//MirrorGameState myCurrentMirror = initialiseMirrorGameState(board);

//		minimax(currentMirror, depth);


		MirrorGameState preserveCurrentMirror = initialiseMirrorGameState(board);

		Integer bestScore = 0;
		Move bestMove = preserveCurrentMirror.getAvailableMoves().asList().get(0);
		for (Move move: preserveCurrentMirror.getAvailableMoves()){
			Pair<Move, Integer> newMiniMax = minimax(preserveCurrentMirror, move, -9999,9999,  depth);
			if (newMiniMax.right() > bestScore) {
				bestScore = newMiniMax.right();
				bestMove = newMiniMax.left();
			}
		}

		return bestMove;




		// var moves = board.getAvailableMoves().asList();
		// Move myMove = moves.get(new Random().nextInt(moves.size()));
		// return myMove;
	}
}

// Our overall structure is good. To make this work, we need to address 3 things
// 1. Our base case in Minimax evaluates score based on the relative position of all players to some detective.
//	  We need to change this to be Mr X's position
// 2. The pseudogamestate we're passing into minimax always has an empty log and its remaining always only has Mr X in it.
//    We need to find a wat to access both of these attributes from board, and mirror their updates in our pseudo game state.
// 3. Addressing the issue above, our current pseudo game state is encapsulated by the factory method, so we need to rewrite
//    that class to get rid of the factory and instead just have a public pseudo game state.