package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
public class UtilityHandler {


    public UtilityHandler(){}

    // Precomputes the number of nodes each node connects to and stores it in an array
    public ArrayList<Integer> computeConnectivity(Board board){

        ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
        ArrayList<Integer> connections = new ArrayList<>();
        for (Integer node: graph.nodes()) {
            connections.add(graph.degree(node));
        }
        return connections;
    }

    // Returns a matrix storing the shortest path from every node to every other node
    public ArrayList<ArrayList<Integer>>  floydWarshall(Board board) {
        ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;
        ArrayList<ArrayList<Integer>> distances = new ArrayList<>();

        // Sets initial values as infinity or 0
        for (Integer node1: graph.nodes()) {
            distances.add(new ArrayList<>());
            for (Integer node2 : graph.nodes()) {
                if (node1 == node2) {
                    distances.get((Integer) node1 - 1).add((Integer) node2 - 1, 0);
                }
                else if (graph.hasEdgeConnecting(node1, node2)) {
                    distances.get((Integer) node1 - 1).add((Integer) node2 - 1, 1);
                }
                else {
                    distances.get((Integer) node1 - 1 ).add((Integer) node2 - 1, 9999);
                }
            }
        }

        for(int k = 0; k<distances.size(); k++ ){
            for(int i = 0; i<distances.size(); i++ ){
                for(int j = 0; j<distances.size(); j++ ){
                    if(distances.get(i).get(j) > distances.get(i).get(k) + distances.get(k).get(j) ){
                        distances.get(i).set(j,(distances.get(i).get(k) + distances.get(k).get(j)));
                    }
                }
            }
        }
        return distances;
    }

    // Instantiate players based off information we know about the board
    public ArrayList<Player> createPlayers(Board board) {
        ImmutableSet<Piece> pieces = board.getPlayers();
        ArrayList<Player> players = new ArrayList<>();

        for (Piece piece : pieces) {
            if (piece.isDetective()){
                // For types of tickets, call .getCount() and add to an immutable map
                HashMap<ScotlandYard.Ticket, Integer> playerTicketCount = new HashMap<>();
                for (ScotlandYard.Ticket ticket : ScotlandYard.DETECTIVE_TICKETS ) {
                    playerTicketCount.put(ticket, board.getPlayerTickets(piece).get().getCount(ticket));;
                }

                Integer location  = board.getDetectiveLocation((Piece.Detective) piece).orElse(-1);
                players.add(new Player(piece, ImmutableMap.copyOf(playerTicketCount), location));
            }
            else if (piece.isMrX()) {
                HashMap<ScotlandYard.Ticket, Integer> mrXTicketCount = new HashMap<>();
                for (ScotlandYard.Ticket ticket : ScotlandYard.MRX_TICKETS ) {
                    mrXTicketCount.put(ticket, board.getPlayerTickets(piece).get().getCount(ticket));;
                }
                int location  = board.getAvailableMoves().asList().get(0).source();
                Player mrX = new Player(piece,ImmutableMap.copyOf(mrXTicketCount), location );
                players.add(0, mrX);
            }
        }
        return players;
    }

    // Returns a copy of a game state based on information about the board.
    public MirrorGameState initialiseMirrorGameState(Board board){
        ArrayList<Player> players = createPlayers(board);
        Player mrX = players.get(0);
        List<Player> detectives = players.subList(1, players.size());
        ImmutableList<LogEntry> log = board.getMrXTravelLog();
        return new MirrorGameState(board.getSetup(), ImmutableSet.of(mrX.piece()), log, mrX, detectives );
    }


    // Splits up a set of moves into secret and not secret.
    public Pair<ArrayList<Move>, ArrayList<Move>> splitSecretMoves(ArrayList<Move> moves) {
        ArrayList<Move> onlySecretMoves = new ArrayList<>();
        ArrayList<Move> notSecretMoves = new ArrayList<>();

        for (Move m : moves) {
            Iterator<ScotlandYard.Ticket> ticketIterator = m.tickets().iterator();
            List<ScotlandYard.Ticket> ticketList = Lists.newArrayList(ticketIterator);
            if (ticketList.contains(ScotlandYard.Ticket.SECRET)) {
                onlySecretMoves.add(m);
            }
            else {
                notSecretMoves.add(m);
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
            if (ticketList.contains(ScotlandYard.Ticket.DOUBLE)) {
                onlyDoubleMoves.add(m);
            }
            else {
                onlySingleMoves.add(m);
            }
        }
        return new Pair<>(onlySingleMoves, onlyDoubleMoves);
    }

    // Wrapper method to fully split moves into all categories.
    public ArrayList<ArrayList<Move>> splitMoves(ArrayList<Move> moves){

        Pair<ArrayList<Move>, ArrayList<Move>> secretAndNotSecretMoves = splitSecretMoves(moves);
        Pair<ArrayList<Move>, ArrayList<Move>> notSecretSingleAndDoubleMoves = splitDoubleMoves(secretAndNotSecretMoves.right());
        Pair<ArrayList<Move>, ArrayList<Move>> secretSingleAndDoubleMoves = splitDoubleMoves(secretAndNotSecretMoves.left());

        ArrayList<ArrayList<Move>> movesSplitUp = new ArrayList<>();
        movesSplitUp.add(new ArrayList<>(notSecretSingleAndDoubleMoves.left()));
        movesSplitUp.add(new ArrayList<>(notSecretSingleAndDoubleMoves.right()));
        movesSplitUp.add(new ArrayList<>(secretSingleAndDoubleMoves.left()));
        movesSplitUp.add(new ArrayList<>(secretSingleAndDoubleMoves.right()));

        return movesSplitUp;
    }


}
