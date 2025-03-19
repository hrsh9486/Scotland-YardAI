package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PairlessMiniMax implements Ai {
    ArrayList<ArrayList<Integer>> distances;
    Move bestMove;
    int bestScore;
    int depth = 7;

    @Nonnull
    @Override
    public String name() {
        return "Doofenshmirtz";
    }

    // Returns the distance between Mr X and the closest detective to him.
    public Integer score(MirrorGameState gameState, Integer destination) {
        Integer minDistance = 9999;
        for (Player p : gameState.getDetectives()) {
            minDistance = min(minDistance, distances.get(destination - 1 ).get(p.location()-1));
        }
        return minDistance;
    }

   public int minimax(MirrorGameState gs, Move move, Integer alpha, Integer beta, Integer depth){
        UtilityHandler utilityHandler = new UtilityHandler();

        // Base case for when we reach desired depth
        if (depth == 0){
            return score(gs, gs.getMrX().location());
        }

        // Maximising Player (Mr X)
        else if (gs.getRemaining().contains(gs.getMrX().piece())){
            // Return high score if Mr X wins in current game state
            if (gs.getWinner().contains(gs.getMrX().piece())){
                return 9999;
            }

            ArrayList<Move> moves = new ArrayList<>(gs.getAvailableMoves());
            // Return high score if Mr X wins in current game state
            if (moves.isEmpty()){
                return -9999;
            }

            Move bestMove = moves.get(0);
            ArrayList<ArrayList<Move>> movesSplitUp = utilityHandler.splitMoves(moves);
            int maxEval = -9999;

            // Iterate through possible moves that Mr X can make in the current game state, and return minimax evaluation.
            for (Move newMove : movesSplitUp.get(0)) {
                MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                int currentScore = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
                if (currentScore > maxEval) {
                    bestMove = newMove;
                    maxEval = currentScore;
                }
                alpha = max(alpha, maxEval);
                if (beta <= alpha) {
                    break;
                }
            }
            if (maxEval < 3){
                for (Move newMove : movesSplitUp.get(1)) {
                    MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                    int currentScore = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
                    if (currentScore > maxEval) {
                        bestMove = newMove;
                        maxEval = currentScore;
                    }
                    alpha = max(alpha, maxEval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            if (depth == this.depth) {
                this.bestMove = bestMove;
                this.bestScore = maxEval;
            }
            return maxEval;
        }

        // Minimising Player (Detectives)

       else{
           int minEval = 9999;
           ArrayList<Move> moves = new ArrayList(gs.getAvailableMoves());
           if (moves.isEmpty()){
               return 9999;
           }
           Piece current = moves.get(0).commencedBy();

           for (Move newMove: moves){
               // Make sure we are only considering one detective at a time.
               if (newMove.commencedBy() == current){
                   MirrorGameState copyState = new MirrorGameState(gs.getSetup(), gs.getRemaining(), gs.getMrXTravelLog(), gs.getMrX(), gs.getDetectives());
                   int currentScore = minimax(copyState.advance(newMove), newMove, alpha, beta, depth - 1);
                   if (currentScore < minEval){
                       minEval = currentScore;
                   }
               }

               beta = min(beta, minEval);
               if (beta <= alpha) {
                   break;
               }
           }
           return minEval;
        }
   }



    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        UtilityHandler utilityHandler = new UtilityHandler();
        if (this.distances == null){ this.distances = utilityHandler.floydWarshall(board);}

        MirrorGameState preserveCurrentMirror = utilityHandler.initialiseMirrorGameState(board);

        // NB THIS IS THROWING ERRORS ON OCASSION, IE. THE GAME ISN'T OVER BUT OUR GETAVAILABLEMOVES THINKS THERE ARE NO MOVES
        // THIS SUGGESTS WE'VE OVER PRUNED THE GAME TREE IN SOME STATES.
        Move bestMove = preserveCurrentMirror.getAvailableMoves().asList().get(0);




        minimax(preserveCurrentMirror, bestMove, -9999,9999,  this.depth);

        return this.bestMove;
    }
}