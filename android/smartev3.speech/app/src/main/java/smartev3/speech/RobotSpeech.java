package smartev3.speech;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import android.content.Context;
import android.os.Debug;
import android.speech.tts.TextToSpeech;

public class RobotSpeech extends Thread implements TextToSpeech.OnInitListener
{
    public static final boolean DEBUG = true;
    public static final int PORT = 5050;

    private MainActivity _activity;
    private String _host;
    private volatile boolean _connecting, _connected;
    private URL _robotSpeechURL;
    private TextToSpeech _textToSpeech;
    private LinkedBlockingQueue<String> _answers = new LinkedBlockingQueue<String>();

    public RobotSpeech(MainActivity activity, String host)
    {
        _activity = activity;
        _host = host;
        try
        {
            _robotSpeechURL = new URL("http://" + _host + ":" + PORT + "/reply.txt");
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public boolean connect()
    {
        _connecting = true;
        start();
        for (int pass = 1; pass <= 100; pass++)
        {
            if (_connected)
            {
                _connecting = false;
                return true;
            }
            sleepForMilliseconds(20);
        }
        _connecting = false;
        return false;
    }

    public void run()
    {
        _textToSpeech = new TextToSpeech(_activity, this);
        _textToSpeech.setLanguage(Locale.US);
        String nextAction = "AWAIT";
        try
        {
            while (true)
            {
                String command = post(nextAction);
                nextAction = run(command);
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            if (_connecting)
            {
                // Don't crash app if connecting for first time.
                return;
            }
            throw ex; // Crash app.
        }
    }

    // TextToSpeech.OnInitListener
    @Override
    public void onInit(int status)
    {
    }

    private String post(String reply)
    {
        try
        {
            if (DEBUG) logDebug("post: " + reply);
            HttpURLConnection conn = (HttpURLConnection)_robotSpeechURL.openConnection();
            conn.setRequestMethod("POST");
            OutputStream output = conn.getOutputStream();
            _connected = true;
            byte[] replyBytes = (reply + "\r\n").getBytes("UTF-8");
            output.write(replyBytes);
            output.flush();
            InputStream input = new BufferedInputStream(conn.getInputStream());
            ByteArrayOutputStream commandBytes = new ByteArrayOutputStream();
            int b;
            while ((b = input.read()) != -1)
            {
                commandBytes.write(b);
            }
            commandBytes.flush();
            input.close();
            String command = new String(commandBytes.toByteArray(), "UTF-8");
            return command;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private String run(String command)
    {
        if (DEBUG) logDebug("run: " + command);
        if (command.startsWith("SAY "))
        {
            return say(command.substring(4));
        }
        else if (command.startsWith("ASK "))
        {
            return ask(command.substring(4));
        }
        else
        {
            throw new IllegalArgumentException("command: " + command);
        }
    }

    private void sleepForMilliseconds(int milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (Exception ignore)
        {
        }
    }

    public String say(String text)
    {
        if (DEBUG) logDebug("say: " + text);
        _textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        sleepForMilliseconds(50);
        while (_textToSpeech.isSpeaking())
        {
            sleepForMilliseconds(1);
        }
        return "AWAIT";
    }

    private String ask(String question)
    {
        if (DEBUG) logDebug("ask: " + question);
        List<String> options = new ArrayList<String>();
        int leftBrace = question.indexOf("[");
        int rightBrace = question.indexOf("]");
        if (leftBrace != -1 && rightBrace > leftBrace)
        {
            String[] array = question.substring(leftBrace + 1, rightBrace).split(",");
            for (String option : array)
            {
                options.add(option);
            }
            question = question.substring(0, leftBrace).trim();
        }
        say(question);
        _activity.startWaitingForInput(options);
        String answer;
        try
        {
            answer = _answers.take();
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }
        return "REPLY " + answer;
    }

    public void addAnswer(String answer)
    {
        if (DEBUG) logDebug("addAnswer: " + answer);
        try
        {
            _answers.put(answer);
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void logDebug(String message)
    {
        if (DEBUG)
        {
            System.err.println("DEBUG [" + Thread.currentThread().getName() + "] smartev3.speech - " + message);
        }
    }
}
