package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.crypto.spec.PSource;

import com.google.common.collect.Lists;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import static java.lang.Math.max;
import static java.lang.Math.min;


public class MyAi implements Ai {
    ArrayList<ArrayList<Integer>> distances;
    ArrayList<Integer> connectivity;
	Integer DEPTH = 100;

	@Nonnull @Override public String name() { return "Doofenshmirtz"; }

    public Pair<Move, Double> minimax(MirrorGameState gs, Move move, Double alpha, Double beta, Integer depth){
        // Base Case, return static evaluation of board based on score function
        UtilityHandler utilityHandler = new UtilityHandler();
        //System.out.println("Am i in? depth: " + depth);
        if (depth == 0){
            //System.out.println("Score: " + score(gs, gs.getMrX().location()) + " Move: " + move);
            return new Pair<Move, Double>(move, score(gs, gs.getMrX().location()));
        }

        // This is for the mrX (the maximising player)
        else if (depth % this.DEPTH == 0){
            // Base cases when we reach the furthest future game state we're considering.
            if(gs.getWinner().contains(gs.getMrX())){
                return new Pair<>(move, 9999.0);
            }
            if (!gs.getWinner().isEmpty()){
                return new Pair<>(move, -9999.0);}

            // Maximising Mr X's distance from the players
            Double maxEval = -9999.0;

            // Split up moves into categories, to conserve secret and double moves. We really should make this a function
            ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
            ArrayList<ArrayList<Move>> movesSplitUp = utilityHandler.splitMoves(moves);


            // If Mr X has no available moves, return negative score.
            if (moves.isEmpty()){ return new Pair<>(move, maxEval);}
            Move bestMove = moves.get(0);

            // Check if it's a reveal move, and if so, use a secret move
            if (!gs.getMrXTravelLog().isEmpty() && gs.getSetup().moves.get(gs.getMrXTravelLog().size() - 1)){
                for (Move newMove: movesSplitUp.get(2)){
                    //make a copy
                    MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                    Pair<Move, Double> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
                    if (currentEval.right()>maxEval){
                        maxEval = currentEval.right();
                        bestMove = newMove;
                    }
                    alpha = max(alpha, maxEval);
                    if (beta<=alpha){break;}
                }
                // If a single secret move is still too close to a detective, consider using secret double moves.
                if (maxEval<3){
                    for (Move newMove: movesSplitUp.get(3)){
                        //make a copy
                        MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                        Pair<Move, Double> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
                        if (currentEval.right()>maxEval){
                            maxEval = currentEval.right();
                            bestMove = newMove;
                        }
                        alpha = max(alpha, maxEval);
                        if (beta<=alpha){break;}
                    }
                }
            }
            // Check log to see if the previous move was a reveal move.
            // Wait we should just check board.moves
            // Iterate through possible single moves that Mr X can take, and recursively assign a score.
            for (Move newMove : movesSplitUp.get(0)) {
                // We have a significant amount of duplicate code here, address.
                //make a copy
                MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                Pair<Move, Double> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
                if (currentEval.right() > maxEval) {
                    maxEval = currentEval.right();
                    bestMove = newMove;
                    System.out.println("Internal scores found: " + maxEval);
                    if (maxEval > 3) {
                        return new Pair<>(bestMove, maxEval);
                    }
                }
                alpha = max(alpha, maxEval);
                if (beta <= alpha) {
                    break;
                }
            }

            // If all single moves are bad, consider double moves.
            if (maxEval < 3) {
                for (Move newMove :  movesSplitUp.get(1)) {
                    //make a copy
                    MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                    Pair<Move, Double> currentEval = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
                    if (currentEval.right() > maxEval) {
                        maxEval = currentEval.right();
                        bestMove = newMove;
                    }
                    alpha = max(alpha, maxEval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return new Pair<Move, Double> (bestMove,maxEval);
        }

        // This is for the detectives (the minimising players)
        else {
            ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
            // If detectives have no available moves, return positive score
            if (!gs.getWinner().isEmpty() || moves.isEmpty()){
                return new Pair<>(move, 9999.0);}
            Double minEval = 9999.0;

            Move bestMove = moves.get(0);

            for (Move newMove: moves){
                //make a copy
                MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                Pair<Move, Double> currentEval = minimax(copyState.advance(newMove), newMove,alpha, beta, depth-1);
                if (currentEval.right() < minEval){
                    minEval = currentEval.right();
                    bestMove = newMove;
                }
                beta = min(beta, minEval);
                if (beta<=alpha){break;}
            }
            return new Pair<Move, Double> (bestMove, minEval);
        }
    }

    public double score(MirrorGameState gs, Integer destination) {
        double minDistance = 9999;
        for (Player p : gs.getDetectives()) {
            // Integer distance = distances.get(destination - 1 ).get(p.location()-1);
            // Instead of just minimising the distance we want an actual score.
            minDistance = min(minDistance, distances.get(destination - 1 ).get(p.location()-1));
        }

        // Only consider connectivity if minDistance
//        if (minDistance < 3) {
//            int connectivity = this.connectivity.get(destination-1);
//            minDistance = (minDistance * 0.8) + (connectivity * 0.2);
//        }
        return minDistance;
    }


    @Nonnull @Override public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {

        // We should only really be running this once. Maybe put it in a text file or something.
        // Or we could check if it's the first move of the game and run it then.
        UtilityHandler utilityHandler = new UtilityHandler();
		if (this.distances == null){this.distances = utilityHandler.floydWarshall(board);}
        if (this.connectivity == null){this.connectivity = utilityHandler.computeConnectivity(board);}
        System.out.println(" ");


        MirrorGameState preserveCurrentMirror = utilityHandler.initialiseMirrorGameState(board);

        Integer bestScore = 0;
        Move bestMove = preserveCurrentMirror.getAvailableMoves().asList().get(0);
        System.out.println("Available moves: " + preserveCurrentMirror.getAvailableMoves());
        System.out.println("Size of available moves: " + preserveCurrentMirror.getAvailableMoves().size());
        Pair<Move, Double>	bestMoveAndScore = minimax(preserveCurrentMirror, bestMove, -9999.0,9999.0,  this.DEPTH);
        System.out.println("Move: " + bestMoveAndScore.left() + " Score: " + bestMoveAndScore.right());
        //System.out.println("Tickets: " + Lists.newArrayList(bestMoveAndScore.left().tickets().iterator()));
        System.out.println("Tickets: " + bestMoveAndScore.left().commencedBy());
        System.out.println("From" + bestMoveAndScore.left().source() + " Could have gone to: " + board.getSetup().graph.adjacentNodes(bestMoveAndScore.left().source()));
        return bestMoveAndScore.left();
    }
}