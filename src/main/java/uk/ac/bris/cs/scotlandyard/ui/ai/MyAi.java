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
	ArrayList<Integer> connectivity;

	// Returns a matrix storing the shortest path from every node to every other node, using
	// Floyd Warshall Algorithm
	public void  floydWarshall(Board board) {
		// Prevents precomputation on every pickMove, only runs it once.
		if (this.distances != null) {
			return;
		}


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

	// Precomputes the number of nodes each node connects to and stores it in an array.
	public void computeConnectivity(Board board){
		if (this.connectivity != null) {
			return;
		}
		ImmutableValueGraph graph = board.getSetup().graph;
		ArrayList<Integer> connections = new ArrayList<>();
		for (Object node: graph.nodes()) {
			connections.add(graph.degree(node));
		}
		this.connectivity = connections;
	}


	@Nonnull @Override public String name() { return "Doofenshmirtz"; }
	
	// Optimisations:
	// (x) splitDoubleMoves - gets rid of double moves from the available moves
	// ( ) removeNextToDetectiveMoves - gets rid of moves where mrX is placed next to a detective
	// (x) prioritiseDoubleMoves - if available moves is 0, then add the double moves in
	// ( ) if there is a secret move and another move to a destination, prioritise secret move only
	// .. if it's a reveal (so you can remove them)
	// ( ) priority queue to prioritise stations and ferries
	// ( ) secret move only if it's a reveal (force secret move on reveal)
	// ( ) Stay away from corner nodes (unless they are stations), so we deprioritise them
	// ( ) If there is a single move and a double move to the same location, use the single move (implement this in removeMoves)
	// (x) If the score is 3 or 4, then stop and take that score
	// we can schedule/prioritise secret moves later

	// What's the point? getAvailableMoves returns a whole bunch of different moves, we want to
	// give mrX a smaller set of moves that are actually useful

	// Splits up a set of moves into normal and secret moves.
	public Pair<ArrayList<Move>, ArrayList<Move>> splitSecretMoves(ArrayList<Move> moves) {
		ArrayList<Move> onlySecretMoves = new ArrayList<>();
		ArrayList<Move> notSecretMoves = new ArrayList<>();

		for (Move m : moves) {
			Iterator<ScotlandYard.Ticket> ticketIterator = m.tickets().iterator();
			List<ScotlandYard.Ticket> ticketList = Lists.newArrayList(ticketIterator);
			if (!ticketList.contains(ScotlandYard.Ticket.SECRET)) {
				notSecretMoves.add(m);
			}
			else {
				onlySecretMoves.add(m);
			}
		}

		return new Pair<>(onlySecretMoves,  notSecretMoves);
	}

	// Splits up a set of moves into single and double moves.
	public Pair<ArrayList<Move>, ArrayList<Move>> splitDoubleMoves(ArrayList<Move> moves) {
		ArrayList<Move> onlySingleMoves = new ArrayList<>();
		ArrayList<Move> onlyDoubleMoves = new ArrayList<>();
		for (Move m: moves){
			Iterator<ScotlandYard.Ticket> ticketIterator = m.tickets().iterator();
			List<ScotlandYard.Ticket> ticketList = Lists.newArrayList(ticketIterator);
			if (!ticketList.contains(ScotlandYard.Ticket.DOUBLE)) {
				onlySingleMoves.add(m);
			}
			else {
				onlyDoubleMoves.add(m);
			}
		}
		return new Pair<>(onlySingleMoves, onlyDoubleMoves);
	}

	public Pair<Move, Integer> minimax(MirrorGameState gs, Move move, Integer alpha, Integer beta, Integer depth){
		// Base Case, return static evaluation of board based on score function
		if (depth == 0){
			//System.out.println("Depth: " + depth + " Score: " + score(gs, gs.getMrX().location()) + " Move: " + move);
			return new Pair<Move, Integer>(move, score(gs, gs.getMrX().location()));
		}

		// This is for the mrX (the maximising player)
		else if (depth % 5 == 0){
			// Base cases when we reach the furthest future game state we're considering.
			if(gs.getWinner().contains(mrX)){
				return new Pair<>(move, 9999);
			}
			if (!gs.getWinner().isEmpty()){
				return new Pair<>(move, -9999);}

			// Maximising Mr X's distance from the players
			Integer maxEval = -9999;

			// Split up moves into categories, to conserve secret and double moves. We really should make this a function
			ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
			Pair<ArrayList<Move>, ArrayList<Move>> secretAndNotSecretMoves = splitSecretMoves(moves);
			Pair<ArrayList<Move>, ArrayList<Move>> notSecretSingleAndDoubleMoves = splitDoubleMoves(secretAndNotSecretMoves.right());
			Pair<ArrayList<Move>, ArrayList<Move>> secretSingleAndDoubleMoves = splitDoubleMoves(secretAndNotSecretMoves.left());

			ArrayList<Move> pureSingleMoves = new ArrayList<>(notSecretSingleAndDoubleMoves.left());
			ArrayList<Move> pureDoubleMoves = new ArrayList<>(notSecretSingleAndDoubleMoves.right());
			
			ArrayList<Move> secretSingleMoves = new ArrayList<>(secretSingleAndDoubleMoves.left());
			ArrayList<Move> secretDoubleMoves = new ArrayList<>(secretSingleAndDoubleMoves.right());

			// If Mr X has no available moves, return negative score.
			if (moves.isEmpty()){ return new Pair<>(move, maxEval);}
			Move bestMove = moves.get(0);

			// Check if it's a reveal move, and if so, use a secret move
			if (!gs.getMrXTravelLog().isEmpty() && gs.getSetup().moves.get(gs.getMrXTravelLog().size() - 1)){
				for (Move newMove: secretSingleMoves){
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
				// If a single secret move is still too close to a detective, consider using secret double moves.
				if (maxEval<3){
					for (Move newMove: secretDoubleMoves){
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
				}
			}
			// Check log to see if the previous move was a reveal move.
			// Wait we should just check board.moves
			// Iterate through possible single moves that Mr X can take, and recursively assign a score.
			for (Move newMove : pureSingleMoves) {
				// We have a significant amount of duplicate code here, address.
				//make a copy
				MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
				Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
				if (currentEval.right() > maxEval) {
					maxEval = currentEval.right();
					bestMove = newMove;
					if (maxEval > 4) {
						return new Pair<>(bestMove, maxEval);
					}
				}
				alpha = max(alpha, currentEval.right());
				if (beta <= alpha) {
					break;
				}
			}

			// If all single moves are bad, consider double moves.
			if (maxEval < 3) {
				for (Move newMove : pureDoubleMoves) {
					//make a copy
					MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
					Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
					if (currentEval.right() > maxEval) {
						maxEval = currentEval.right();
						bestMove = newMove;
					}
					alpha = max(alpha, currentEval.right());
					if (beta <= alpha) {
						break;
					}
				}
			}

			return new Pair<Move, Integer> (bestMove,maxEval);
		}

		// This is for the detectives (the minimising players)
		else {
			ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
			// If detectives have no available moves, return positive score
			if (!gs.getWinner().isEmpty() || moves.isEmpty()){
				return new Pair<>(move, 9999);}
			Integer minEval = 9999;

			Move bestMove = moves.get(0);

			for (Move newMove: moves){
				//make a copy
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
			// Integer distance = distances.get(destination - 1 ).get(p.location()-1);
			// Instead of just minimising the distance we want an actual score.
			minDistance = min(minDistance, distances.get(destination - 1 ).get(p.location()-1));
		}

		// Only consider connectivity if minDistance

		return minDistance;
	}

	// We really should try and avoid having attributes within the class.
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

		// We should only really be running this once. Maybe put it in a text file or something.
		// Or we could check if it's the first move of the game and run it then.
		floydWarshall(board);
		computeConnectivity(board);
		System.out.println(" ");

		int playersPlaying = board.getPlayers().size();
		// We need to consider this
		int depth = playersPlaying * 1;
		depth = 5;
		// Build a new game state, preserve is a copy, my is to run minimax on
		//MirrorGameState myCurrentMirror = initialiseMirrorGameState(board);


		MirrorGameState preserveCurrentMirror = initialiseMirrorGameState(board);

		Integer bestScore = 0;
		Move bestMove = preserveCurrentMirror.getAvailableMoves().asList().get(0);

		Pair<Move, Integer>	bestMoveAndScore = minimax(preserveCurrentMirror, bestMove, -9999,9999,  depth);

		return bestMoveAndScore.left();
		// var moves = board.getAvailableMoves().asList();
		// Move myMove = moves.get(new Random().nextInt(moves.size()));
		// return myMove;
	}
}
