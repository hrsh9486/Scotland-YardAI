package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Player;
import static java.lang.Math.min;
import static java.lang.Math.max;

public class PerryThePlatypus implements Ai {
    ArrayList<ArrayList<Integer>> distances;
    UtilityHandler utilityHandler = new UtilityHandler();
    public int round;
    public int lastKnownMrXPosition = -1;

    @Nonnull
    @Override
    public String name() {
        return "Perry the platypus";
    }

    // Returns distance between input position and closest detective.
    public Integer score(MirrorGameState gameState, Integer destination) {
        Integer minDistance = 9999;
        for (Player p : gameState.getDetectives()) {
            minDistance = min(minDistance, distances.get(destination - 1).get(p.location() - 1));
        }
        // Only consider connectivity if minDistance
        return minDistance;
    }


    @Nonnull
    @Override
    public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {

        if (this.distances == null) {
            this.distances = this.utilityHandler.floydWarshall(board);
        }
        MirrorGameState preserveCurrentMirror = utilityHandler.initialiseMirrorGameState(board);
        this.round = board.getMrXTravelLog().size();
        this.lastKnownMrXPosition = board.getMrXTravelLog().get(this.round - 1).location().orElse(this.lastKnownMrXPosition);
        ArrayList<Move> moves = new ArrayList<>(board.getAvailableMoves());
        Move bestMove = moves.get(new Random().nextInt(moves.size()));
        TypeCheckVisitor typeCheckVisitor = new TypeCheckVisitor();


        if (lastKnownMrXPosition == -1) {
            // Try to spread out as much as possible
            int bestScore = 0;
            for (Move move : moves) {
                int newScore = score(preserveCurrentMirror, (int) move.accept(typeCheckVisitor));
                if (newScore > bestScore) {
                    bestMove = move;
                    bestScore = newScore;
                }
            }
        }

        else {
            // If Mr X's position is revealed, take move which minimises distance between detectives and Mr X.
            int bestScore = 9999;
            for (Move move : moves) {
                int currentScore = distances.get(lastKnownMrXPosition -1).get((int) move.accept(typeCheckVisitor) -1);
                if (currentScore < bestScore) {
                    bestMove = move;
                    bestScore = currentScore;
                }
            }
        }
        return bestMove;
    }
}
