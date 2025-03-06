package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Move;

public interface Visitor<T> {
    T visit(Move.SingleMove move);
    T visit(Move.DoubleMove move);
}
