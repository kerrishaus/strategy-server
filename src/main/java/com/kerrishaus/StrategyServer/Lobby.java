package com.kerrishaus.StrategyServer;

import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.*;

public class Lobby
{
    StrategyServer server;

    public String id;
    public int    ownerId;

    private int     turnCounter;
    public int      currentTurnClientId;
    public int      currentTurnStageId;

    public boolean started = false;
    public boolean paused  = false;

    public Map<Integer, Client> clients = new HashMap<>();

    public ArrayList<Integer> clientTurnOrder = new ArrayList<>();

    public Lobby(StrategyServer server, String id, int ownerId)
    {
        this.server  = server;
        this.id      = id;
        this.ownerId = ownerId;
    }

    public void broadcast(final String string)
    {
        this.clients.forEach((clientId, client) -> {
            client.connection.send(string);
        });
    }

    public void startGame(final int fromClientId)
    {
        if (fromClientId != this.ownerId)
        {
            System.out.println(fromClientId + " requested to start the game, but they are not the owner. Ignoring.");
            return;
        }

        if (this.clients.size() < 2)
        {
            System.out.println(fromClientId + " requested to start the game, but there are not enough players. Ignoring.");
            return;
        }

        this.currentTurnClientId = this.clientTurnOrder.get(0);
        this.currentTurnStageId  = 0;
        this.turnCounter         = 0;

        this.started = true;

        final JSONObject command = new JSONObject();
        command.put("command", "startGame");

        this.broadcast(command.toString());

        System.out.println("Started game in lobby " + this.id);
    }

    public void nextTurn()
    {
        System.out.println("Current Turn:" + this.turnCounter + " Max Turns: " + this.clientTurnOrder.size());

        if (this.turnCounter >= this.clientTurnOrder.size() - 1)
        {
            System.out.println("Resetting turn counter.");
            this.turnCounter = 0;
        }
        else
            this.turnCounter += 1;

        this.currentTurnStageId  = 0;
        this.currentTurnClientId = this.clientTurnOrder.get(this.turnCounter);

        final JSONObject command = new JSONObject();
        command.put("command" , "nextTurn");
        command.put("clientId", this.currentTurnClientId);

        this.broadcast(command.toString());

        System.out.println("Started turn for " + this.currentTurnClientId + " (#" + this.turnCounter + " in queue)");
    }

    public void nextStage(final int clientId)
    {
        if (clientId != this.currentTurnClientId)
        {
            System.out.println(clientId + " requested next stage, but it is not their turn. Current turn is client " + this.currentTurnClientId);
            return;
        }

        if (this.currentTurnStageId == 2)
        {
            System.out.println("No more stages for " + this.currentTurnClientId + "'s turn, next turn.");
            nextTurn();
            return;
        }

        this.currentTurnStageId++;

        final JSONObject command = new JSONObject();
        command.put("command", "setStage");
        command.put("stageId", this.currentTurnStageId);

        this.broadcast(command.toString());

        System.out.println("Starting next stage " + this.currentTurnStageId + " for client " + this.currentTurnClientId);
    }

    public void selectTerritory(final int clientId, final int territoryId)
    {
        if (clientId != currentTurnClientId)
        {
            System.out.println(clientId + " tried to select territory " + territoryId + " but it was not their turn, ignoring.");
            return;
        }

        final JSONObject command = new JSONObject();
        command.put("command"    , "selectTerritory");
        command.put("territoryId", territoryId);
        this.broadcast(command.toString());
    }

    public void deselectTerritory(final int clientId, final int territoryId)
    {
        if (clientId != currentTurnClientId)
        {
            System.out.println(clientId + " tried to deselect territory " + territoryId + " but it was not their turn, ignoring.");
            return;
        }

        final JSONObject command = new JSONObject();
        command.put("command"    , "deselectTerritory");
        command.put("territoryId", territoryId);
        this.broadcast(command.toString());
    }

    public void attack(final int clientId, final int fromTerritoryId, final int toTerritoryId)
    {
        if (clientId != currentTurnClientId)
        {
            System.out.println(clientId + " tried to attack territory " + toTerritoryId + " from " + fromTerritoryId + " but it was not their turn, ignoring.");
            return;
        }

        final JSONObject command = new JSONObject();
        command.put("command", "deselectTerritory");
        command.put("attack" , toTerritoryId);
        command.put("from"   , fromTerritoryId);
        this.broadcast(command.toString());
    }

    public void addClient(Client client)
    {
        this.clients.put(client.id, client);
        client.lobbyId = this.id;

        this.clientTurnOrder.add(client.id);

        System.out.println("Added client " + client.id + " to lobby " + this.id + ".");

        final JSONObject command = new JSONObject();
        command.put("command", "clientJoin");
        command.put("clientId", client.id);

        this.broadcast(command.toString());
    }

    public void removeClient(Client client)
    {
        this.clients.remove(client.id);
        client.lobbyId = null;

        System.out.println("Removed client " + client.id + " from lobby " + this.id + ". There are now " + this.clients.size() + " clients in this lobby.");

        final JSONObject command = new JSONObject();
        command.put("command", "clientLeave");
        command.put("clientId", client.id);

        this.broadcast(command.toString());

        if (this.clients.size() <= 0)
        {
            System.out.println("Closing lobby " + this.id + " because it is empty.");
            this.server.closeLobby(this.id);
        }
    }
}
