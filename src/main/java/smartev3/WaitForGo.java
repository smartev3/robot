package smartev3;

import java.io.*;
import java.net.*;
import java.util.*;

public class WaitForGo
{
    private volatile boolean clientConnected = false;

    public static void main(String[] args)
    {
        if (args.length == 3 && args[0].equals("client"))
        {
            new WaitForGo().runClient(args[1], Integer.parseInt(args[2]));
            return;
        }
        if (args.length == 2 && args[0].equals("server"))
        {
            new WaitForGo().runServer(Integer.parseInt(args[1]));
            return;
        }
        throw new RuntimeException("Invalid options!");
    }

    public boolean hasClient()
    {
        return clientConnected;
    }

    public void runClient(String serverHost, int port)
    {
        System.out.println("Connecting to robot at " + serverHost + ":" + port + "...");
        for (;;)
        {
            try
            {
                Socket socket = new Socket(serverHost, port);
                Writer writer = new OutputStreamWriter(socket.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.print("Waiting for 'go' command from robot...");
                System.out.flush();
                // Send "go" to server.
                writer.write("go\n");
                writer.flush();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    // Await "go" from server.
                    line = line.trim();
                    System.out.println("Robot said '" + line + "'.");
                    if (line.equals("go"))
                    {
                        break;
                    }
                }
                writer.close();
                reader.close();
                socket.close();
                return;
            }
            catch (Exception ex)
            {
                System.out.print(".");
                System.out.flush();
                try
                {
                    Thread.sleep(5000);
                }
                catch (Exception ignore)
                {
                }
            }
        }
    }

    public void runServer(int port)
    {
        ServerSocket listener;
        try
        {
            listener = new ServerSocket(port);
        }
        catch (Exception ex)
        {
            System.err.println("Server socket error!");
            ex.printStackTrace();
            return;
        }
        System.out.println("Listening on port " + port + "...");
        while (true)
        {
            try
            {
                Socket socket = listener.accept();
                Writer writer = new OutputStreamWriter(socket.getOutputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null)
                {
                    // Await "go" from client.
                    line = line.trim();
                    if (line.equals("go"))
                    {
                        break;
                    }
                }
                clientConnected = true;
                awaitSignal();
                // Send "go" to client.
                writer.write("go\n");
                writer.flush();
                writer.close();
                reader.close();
                clientConnected = false;
            }
            catch (Exception ex)
            {
                System.err.println("Failed to communicate with client!");
                ex.printStackTrace();
            }
        }
    }

    private List<Object> signals = new ArrayList<Object>();

    public void awaitSignal()
    {
        // Wait for signal from another part of the application that client should proceed.
        synchronized (signals)
        {
            try
            {
                signals.wait();
                if (signals.size() != 0)
                {
                    signals.clear();
                    return;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public void signal()
    {
        // Produce a signal for awaitSignal to find.
        synchronized (signals)
        {
            signals.add(new Object());
            signals.notify();
        }
    }
}
