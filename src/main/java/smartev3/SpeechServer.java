package smartev3;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class SpeechServer extends Thread
{
    public static boolean DEBUG = false;

    public static int PORT = 5050;

    private int _port;
    private boolean _busy = false;
    private final LinkedBlockingQueue<String> _commands = new LinkedBlockingQueue<String>();
    private final LinkedBlockingQueue<String> _replies = new LinkedBlockingQueue<String>();
    private static volatile boolean _clientConnected = false;

    public SpeechServer(int port)
    {
        setDaemon(true);
        _port = port;
    }

    public static void main(String[] args)
    {
        if (args.length == 2 && args.length == 2)
        {
            new SpeechServer(Integer.parseInt(args[1])).test();
        }
        throw new RuntimeException("Invalid options!");
    }

    private void test()
    {
        start();
        for (int pass = 1;; pass++)
        {
            say("Hello pass " + pass + ".");
            List<String> choices = new ArrayList<String>(2);
            choices.add("Yes");
            choices.add("No");
            WordAliases aliases = new WordAliases();
            String answer = ask("Do you like kiwifruit?", choices, aliases);
            say("You said " + answer + ".");
            sleepForMilliseconds(10000);
        }
    }

    public static boolean hasClient()
    {
        return _clientConnected;
    }

    public void end()
    {
        if (DEBUG) logDebug("end");
        if (!hasClient())
        {
            return;
        }
        addCommand("END");
        long start = System.currentTimeMillis();
        while (_busy)
        {
            // Wait until we know the message has been processed.
            sleepForMilliseconds(5);
            long now = System.currentTimeMillis();
            long elapsed = now - start;
            if (elapsed > 500)
            {
                break; // Don't wait too long.
            }
        }
    }

    public void say(String text)
    {
        if (DEBUG) logDebug("say: " + text);
        String command = "SAY " + text;
        addCommand(command);
        while (_busy)
        {
            // Wait until we know the text has been said.
            sleepForMilliseconds(5);
        }
    }

    public String ask(String question, List<String> answers, WordAliases aliases)
    {
        if (!question.endsWith(".") && !question.endsWith("?"))
        {
            question += "?";
        }
        if (DEBUG) logDebug("ask: " + question);
        String prefix = "";
        int startDelay = 5000, retryDelay = startDelay;
        while (true)
        {
            // Wait for answer.
            String command = "ASK " + prefix + question + " " + answers.toString();
            addCommand(command);
            prefix = "";
            String originalReply = nextReply().toLowerCase();
            if (DEBUG) logDebug("nextReply (received): " + originalReply);
            String resolvedReply = aliases.resolve(originalReply);
            String replyWithSpace = resolvedReply + " ";
            if (DEBUG) logDebug("nextReply (resolved): " + resolvedReply);
            for (String answer : answers)
            {
                if (DEBUG) logDebug("checkAnswer: " + answer);
                String answerWithSpace = answer.toLowerCase() + " ";
                if (replyWithSpace.startsWith(answerWithSpace))
                {
                    return answer;
                }
            }
            if (originalReply.equals("<none>"))
            {
                prefix = "Sorry, I didn't hear your answer. ";
            }
            else if (originalReply.startsWith("speech recognition error "))
            {
                prefix = "Sorry, I had a temporary malfunction; " + originalReply + ". ";
            }
            else if (!resolvedReply.equals("help"))
            {
                prefix = "It sounded to me like you said " + originalReply + ". ";
            }
            prefix += "Please answer ";
            int n = answers.size();
            for (int i = 0; i < n; i++)
            {
                String answer = answers.get(i);
                if (i > 0)
                {
                    prefix += (i + 1 == n ? " or " : ", ");
                }
                prefix += answer;
            }
            prefix += ". ";
            System.out.println(prefix + question); // Help user with pronunciation if they can read the text.
        }
    }

    public void addCommand(String command)
    {
        try
        {
            _commands.put(command);
            _busy = true;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public String nextReply()
    {
        try
        {
            return _replies.take();
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void run()
    {
        System.out.println("Listening on port " + _port + "...");
        ServerSocket listener;
        try
        {
            listener = new ServerSocket(_port);
        }
        catch (Exception ex)
        {
            System.err.println("Failed to create server socket!");
            ex.printStackTrace();
            return;
        }
        try
        {
            while (true)
            {
                final Socket socket = listener.accept();
                start(new Thread(new Runnable() { public void run() { process(socket); } }));
            }
        }
        catch (Exception ex)
        {
            System.err.println("Failed to accept client connection!");
            ex.printStackTrace();
        }
    }

    private void start(Thread thread)
    {
        thread.setDaemon(true);
        thread.start();
    }

    private void process(Socket socket)
    {
        _clientConnected = true;
        try
        {
            OutputStream output = new BufferedOutputStream(socket.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (true)
            {
                String request = readRequest(reader);
                if (request == null)
                {
                    break;
                }
                if (request.startsWith("GET ") && request.contains("/speech.html"))
                {
                    writeResponse(output, 200, "text/html", getResource("speech.html"));
                    continue;
                }
                if (request.equals("AWAIT") || request.startsWith("REPLY "))
                {
                    writeResponse(output, 200, "text/html", nextCommand(request));
                    continue;
                }
                writeResponse(output, 404, "text/plain", "Not Found: " + request);
            }
            output.close();
            reader.close();
        }
        catch (Exception ex)
        {
            System.err.println("Failed to communicate with client!");
            ex.printStackTrace();
        }
    }

    private String readRequest(BufferedReader reader) throws Exception
    {
        if (DEBUG) logDebug("readRequest");
        String requestLine = reader.readLine();
        if (requestLine == null)
        {
            if (DEBUG) logDebug("disconnected");
            return null;
        }
        else
        {
            if (DEBUG) logDebug("requestLine: " + requestLine);
        }
        String httpMethod = beforeFirst(requestLine, " ");
        while (true)
        {
            String headerLine = reader.readLine();
            if (headerLine == null) return null;
            headerLine = headerLine.trim();
            if (headerLine.length() == 0) break;
        }
        if (httpMethod.equals("GET"))
        {
            return requestLine;
        }
        if (httpMethod.equals("POST") && requestLine.contains("/reply.txt"))
        {
            String contentLine = reader.readLine();
            if (DEBUG) logDebug("contentLine: " + contentLine);
            return contentLine;
        }
        return requestLine;
    }

    private void writeOutput(OutputStream output, String text) throws Exception
    {
        output.write(text.getBytes("UTF-8"));
    }

    private void writeResponse(OutputStream output, int status, String contentType, String payloadText) throws Exception
    {
        if (DEBUG) logDebug("writeResponse: " + payloadText);
        byte[] payloadBytes = null;
        if (payloadText != null)
        {
            payloadBytes = payloadText.getBytes("UTF-8");
        }
        writeOutput(output, "HTTP/1.1 " + status + " Status_" + status + "\r\n");
        if (payloadBytes != null)
        {
            writeOutput(output, "Content-Type: " + contentType + "\r\n");
            writeOutput(output, "Content-Length: " + payloadBytes.length + "\r\n");
        }
        writeOutput(output, "\r\n");
        if (payloadBytes != null)
        {
            output.write(payloadBytes);
        }
        output.flush();
    }

    private String getResource(String file) throws Exception
    {
        if (DEBUG) logDebug("getResource: " + file);
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
        if (input == null) throw new RuntimeException("Missing Resource: " + file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
        {
            text.append(line);
            text.append('\n');
        }
        reader.close();
        return text.toString();
    }

    private String nextCommand(String request) throws Exception
    {
        if (request.startsWith("REPLY "))
        {
            String reply = request.substring(6);
            _replies.put(reply);
        }
        if (_commands.isEmpty())
        {
            _busy = false;
        }
        return _commands.take();
    }

    private String beforeFirst(String text, String searchFor)
    {
        int pos = text.indexOf(searchFor);
        if (pos == -1) return text;
        return text.substring(0, pos);
    }

    private void logDebug(String message)
    {
        if (DEBUG)
        {
            long elapsed = System.nanoTime() / 1000000;
            System.out.println(elapsed + " DEBUG [" + Thread.currentThread().getName() + "] smartev3.SpeechServer - " + message);
        }
    }

    private void sleepForMilliseconds(long milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (Exception ignore)
        {
        }
    }
}
