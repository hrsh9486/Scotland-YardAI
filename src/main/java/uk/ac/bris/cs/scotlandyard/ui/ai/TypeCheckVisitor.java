package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.Move;

public class TypeCheckVisitor<Integer> implements Move.Visitor<java.lang.Integer> {
    @Override
    public java.lang.Integer visit(Move.SingleMove move) {
        return move.destination;
    }

    @Override
    public java.lang.Integer visit(Move.DoubleMove move) {
        return move.destination2;
    }
}
