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
    int DEPTH = 9;

	@Nonnull @Override public String name() { return "Old Doofenshmirtz (Defunct)"; }

	public Pair<Move, Integer> minimax(MirrorGameState gs, Move move, Integer alpha, Integer beta, Integer depth){
        UtilityHandler utilityHandler = new UtilityHandler();
		// Base Case, return static evaluation of board based on score function
		if (depth == 0){
			//System.out.println("Depth: " + depth + " Score: " + score(gs, gs.getMrX().location()) + " Move: " + move);
			return new Pair<Move, Integer>(move, score(gs, gs.getMrX().location()));
		}

		// This is for the mrX (the maximising player)
		else if (gs.getRemaining().contains(gs.getMrX().piece())){
			// Base cases when we reach the furthest future game state we're considering.
			if(gs.getWinner().size() == 1){
				System.out.println("Mr X wins game on move " + move);
				return new Pair<>(move, 9999);
			}

			// Maximising Mr X's distance from the players
			Integer maxEval = -9999;

			// Split up moves into categories, to conserve secret and double moves. We really should make this a function
			ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
			if (moves.isEmpty()){
				System.out.println("Moves is empty for Mr X on depth " + depth);
				return new Pair<>(move, 9999);
			}

			ArrayList<ArrayList<Move>> movesSplitUp = utilityHandler.splitMoves(moves);
			Move bestMove = moves.get(0);
			// Check if it's a reveal move, and if so, use a secret move
//			if (!gs.getMrXTravelLog().isEmpty() && gs.getSetup().moves.get(gs.getMrXTravelLog().size() - 1)){
//				for (Move newMove: secretSingleMoves){
//					//make a copy
//					MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
//					Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
//					if (currentEval.right()>maxEval){
//						maxEval = currentEval.right();
//						bestMove = newMove;
//					}
//					alpha = max(alpha, currentEval.right());
//					if (beta<=alpha){break;}
//				}
//				// If a single secret move is still too close to a detective, consider using secret double moves.
//				if (maxEval<3){
//					for (Move newMove: secretDoubleMoves){
//						//make a copy
//						MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
//						Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
//						if (currentEval.right()>maxEval){
//							maxEval = currentEval.right();
//							bestMove = newMove;
//						}
//						alpha = max(alpha, currentEval.right());
//						if (beta<=alpha){break;}
//					}
//				}
//			}

			// Iterate through possible single moves that Mr X can take, and recursively assign a score.
			for (Move newMove : movesSplitUp.get(0)) {
				// We have a significant amount of duplicate code here, address.
				//make a copy
				MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
				Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
				if (currentEval.right() > maxEval) {
					maxEval = currentEval.right();
					bestMove = newMove;
					if (maxEval > 4) {
						System.out.println("Mr X found a move with score > 4");
						return new Pair<>(bestMove, maxEval);
					}
				}
				alpha = max(alpha, currentEval.right());
				if (beta <= alpha) {
					break;
				}
			}

			// If all single moves are bad, consider double moves.
			if (maxEval < 2) {
				for (Move newMove : movesSplitUp.get(1)) {
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
			// If detectives have no available moves, return positive score.
			if (gs.getWinner().size() == 1 || moves.isEmpty()){
				return new Pair<>(move, 9999);}
			Integer minEval = 9999;

			Move bestMove = moves.get(0);
			Piece current  = bestMove.commencedBy();

			for (Move newMove: moves){
				// Ensures that for a given minimax depth, we only consider one player.
				if (newMove.commencedBy().equals(current)) {
					// If there's a state where the detectives win, return it immediately
					if (gs.getWinner().size() > 1) {
						System.out.println("gs.getWinner().size() > 1" + move);
						return new Pair<>(move, -9999);
					}
					//make a copy
					MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
					Pair<Move, Integer> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
					if (currentEval.right() < minEval) {
						minEval = currentEval.right();
						bestMove = newMove;
						// System.out.println("Detective is maximising a move");
					}
					beta = min(beta, currentEval.right());
					if (beta <= alpha) {
						break;
					}
				}
			}
			return new Pair<Move, Integer> (bestMove, minEval);
		}
	}

	
	public Integer score(MirrorGameState gameState, Integer destination) {
		Integer minDistance = 9999;
		for (Player p : gameState.getDetectives()) {
			// Integer distance = distances.get(destination - 1 ).get(p.location()-1);
			// Instead of just minimising the distance we want an actual score.
			minDistance = min(minDistance, distances.get(destination - 1 ).get(p.location()-1));
		}

		// Only consider connectivity if minDistance

		return minDistance;
	}


	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		// We should only really be running this once. Maybe put it in a text file or something.
		// Or we could check if it's the first move of the game and run it then.
        UtilityHandler utilityHandler = new UtilityHandler();
		if (this.distances == null){this.distances = utilityHandler.floydWarshall(board);}

		int playersPlaying = board.getPlayers().size();
		int depth = playersPlaying * 1;
		depth = this.DEPTH;
		// Build a new game state, preserve is a copy, my is to run minimax on
		//MirrorGameState myCurrentMirror = initialiseMirrorGameState(board);


		MirrorGameState preserveCurrentMirror = utilityHandler.initialiseMirrorGameState(board);

		Move bestMove = preserveCurrentMirror.getAvailableMoves().asList().get(0);
		Pair<Move, Integer>	bestMoveAndScore = minimax(preserveCurrentMirror, bestMove, -9999,9999,  depth);

		return bestMoveAndScore.left();
		// var moves = board.getAvailableMoves().asList();
		// Move myMove = moves.get(new Random().nextInt(moves.size()));
		// return myMove;
	}
}
