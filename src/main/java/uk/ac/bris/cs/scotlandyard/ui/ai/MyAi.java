package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.lang.reflect.AnnotatedArrayType;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraph;
import io.atlassian.fugue.Pair;
import nonapi.io.github.classgraph.json.JSONUtils;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.model.Setup;

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
		System.out.println(graph.nodes().size());
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


	//gametree function
	// We need to take our base state, add a node for every possible move, repeat for every player


	public Pair<Move, Integer> minimax(Board.GameState gs, Move move, Integer depth, boolean mrXTurn ){
		if (depth == 0){
			//return  move which minimizes score(gs,mrX.location());
			return new Pair<> (move, score(gs,move.source()));
		}
		if (mrXTurn){
			Integer maxEval = -9999;
			Pair<Move, Integer> bestEval = new Pair<>(move,maxEval);
			for (Move m: gs.getAvailableMoves()){
				Pair<Move, Integer> eval = minimax(gs.advance(m), m, depth-1, false);
				if (eval.right() > bestEval.right()) {
					bestEval = eval;
				}
			}
			return bestEval;
		}

		else {
			Integer minEval = 9999;
			Pair<Move, Integer> bestEval = new Pair<>(move,  minEval);
			for (Move m: gs.getAvailableMoves()){
				Pair<Move, Integer> eval = minimax(gs.advance(m),m , depth-1, true);
				if (eval.right()< bestEval.right()){
					bestEval = eval;
				}
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
		this.detectives = detectives;
	}

	public Board.GameState constructGameState (Board board){
		PseudoGameStateFactory factory = new PseudoGameStateFactory();
		ImmutableSet<Move> moves = board.getAvailableMoves();

		createPlayers(board);
		Player mrX = this.mrX;
		List detectives = this.detectives;
		return factory.build(board.getSetup(), mrX, ImmutableList.copyOf(detectives));
	}

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {


		Board.GameState myGameState = constructGameState(board);

		return minimax(Board.GameState, )


		var moves = board.getAvailableMoves().asList();
		return moves.get(new Random().nextInt(moves.size()));
	}
}
