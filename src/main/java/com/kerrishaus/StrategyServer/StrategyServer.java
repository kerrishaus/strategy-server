// TODO: some message when the server is completely closed
// TODO: gracefully shutdown the server

package com.kerrishaus.StrategyServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

public class StrategyServer extends WebSocketServer
{
    public static void main(String[] args) throws Exception
    {
        String host = "localhost";
        int port = 27002;

        System.out.println("Starting Strategy game server...");

        StrategyServer server = new StrategyServer(new InetSocketAddress(host, port));

        Runtime.getRuntime().addShutdownHook(new ShutdownThread(server));

        server.setConnectionLostTimeout(3);
        server.start();
    }

    int lifetimeClients = 0;

    public HashMap<String, Client> clients = new HashMap<>();
    public HashMap<String, Lobby> lobbies = new HashMap<>();

    public StrategyServer(InetSocketAddress address)
    {
        super(address);
    }

    @Override
    public void onStart()
    {
        System.out.println("Server started on " + this.getPort() + " successfully.");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        this.clients.put(conn.getRemoteSocketAddress().toString(), new Client(conn, lifetimeClients++));

        System.out.println("New connection established: " + conn.getRemoteSocketAddress());

        conn.send("welcome");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        clients.remove(conn.getRemoteSocketAddress().toString());

        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        System.out.println(conn.getRemoteSocketAddress() + " > " + message);

        final Client client = clients.get(conn.getRemoteSocketAddress().toString());

        JSONObject command = new JSONObject(message);

        if (command.getString("command").equals("joinLobby"))
        {
            final String lobbyId = command.getString("lobbyId");

            if (!lobbies.containsKey(lobbyId))
            {
                final JSONObject response = new JSONObject();
                response.put("command", "invalidLobbyId");
                response.put("lobbyId", lobbyId);
                conn.send(response.toString());

                System.out.println("Client requested to join lobby with ID " + lobbyId + " which does not exist.");
            }
            else
            {
                final Lobby lobby = lobbies.get(lobbyId);
                lobby.addClient(client);
                client.lobbyId = lobby.id;

                final JSONObject response = new JSONObject();
                response.put("command", "joinedLobby");
                response.put("lobbyId", lobbyId);
                conn.send(response.toString());
            }
        }
        else if (command.getString("command").equals("createLobby"))
        {
            final String lobbyId = command.getString("lobbyId");

            if (lobbies.containsKey(lobbyId))
            {
                final JSONObject response = new JSONObject();
                response.put("command", "invalidLobbyId");
                response.put("lobbyId", lobbyId);
                conn.send(response.toString());

                System.out.println("Client requested to create lobby with ID " + lobbyId + " which already exists.");

                return;
            }

            Lobby newLobby = new Lobby(this, lobbyId);
            newLobby.addClient(client);
            client.lobbyId = newLobby.id;
            lobbies.put(lobbyId, newLobby);

            final JSONObject response = new JSONObject();
            response.put("command", "joinedLobby");
            response.put("lobbyId", lobbyId);
            conn.send(response.toString());
        }
        else if (command.getString("command").equals("changeState"))
        {
            // TODO: find lobby id by the sender of the message
            final String stateId = command.getString("stateId");
        }
        else
        {
            System.out.println("Unknown command.");

            final JSONObject response = new JSONObject();
            response.put("command", "unknownCommand");
            response.put("originalCommand", command.get("command"));
            conn.send(response.toString());
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message)
    {
        System.out.println(conn.getRemoteSocketAddress() + " sent a byte buffer of size: " + message.limit());
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        System.err.println("ERROR: " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    public void kickClient(Client client, String reason)
    {
        this.broadcast("clientLeft;" + client.id + ";" + reason);

        client.connection.close();

        // client is removed from StrategyServer#clients by the StrategyServer#onClose method.
    }
}