package smartev3.speech;

import java.io.*;
import java.util.*;
import android.content.*;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.speech.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends AppCompatActivity implements RecognitionListener
{
    /* Needs permissions in AndroidManifest.xml:
       <uses-permission android:name="android.permission.INTERNET"/>
       <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    */

    // https://www.androidhive.info/2014/07/android-speech-to-text-tutorial/
    private static final int REQUEST_CODE_SPEECH = 100;
    private static final String ROBOT_HOST_FILE = "robot.host";

    private static final String TAP_TO_SPEAK = "Tap to Speak";
    private static final String WAITING_FOR_ROBOT = "(Waiting for Robot)";
    private static final String WAITING_FOR_USER = "(Waiting for User)";

    private RobotSpeech _robotSpeech;
    private SpeechRecognizer _speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        String filesDir = this.getFilesDir().getAbsolutePath();
        String hostFile = filesDir + "/" + ROBOT_HOST_FILE;
        EditText edit = findViewById(R.id.robotHost);
        edit.setText("192.168.1.100"); // default only
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(hostFile)));
            String line = reader.readLine();
            reader.close();
            if (line != null)
            {
                edit.setText(line);
            }
        }
        catch (IOException ignore)
        {
            // OK since we already set default edit text.
        }
    }

    @Override
    protected void onDestroy()
    {
        if (_speechRecognizer != null)
        {
            _speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    public void buttonOnClick(View view)
    {
        Button button = (Button)view;
        Button connect = findViewById(R.id.connect);
        final String text = button.getText().toString();
        if (button == connect)
        {
            if (text.equals("CONNECT"))
            {
                String filesDir = this.getFilesDir().getAbsolutePath();
                String hostFile = filesDir + "/" + ROBOT_HOST_FILE;
                EditText edit = findViewById(R.id.robotHost);
                String host = edit.getText().toString();
                _robotSpeech = new RobotSpeech(this, host);
                if (_robotSpeech.connect())
                {
                    button.setText(WAITING_FOR_ROBOT);
                    try
                    {
                        Writer writer = new OutputStreamWriter(new FileOutputStream(hostFile));
                        writer.write(host + "\n");
                        writer.close();
                    }
                    catch (IOException ex)
                    {
                        throw new RuntimeException(ex);
                    }
                }
            }
            else if (text.equals(TAP_TO_SPEAK))
            {
                connect.setText(WAITING_FOR_USER);
                promptForSpeechInput();
            }
        }
        else if (text.length() > 0)
        {
            if (connect.getText().equals(TAP_TO_SPEAK))
            {
                clearButtons(WAITING_FOR_ROBOT);
                Thread addAnswer = new Thread()
                {
                    public void run()
                    {
                        _robotSpeech.say(text);
                        sleepForMilliseconds(1000);
                        _robotSpeech.addAnswer(text);
                    }
                };
                addAnswer.start();
            }
        }
    }

    private void clearButtons(String mainText)
    {
        Button connect = findViewById(R.id.connect);
        connect.setText(mainText);
        Button one = findViewById(R.id.b_one);
        Button two = findViewById(R.id.b_two);
        Button three = findViewById(R.id.b_three);
        Button four = findViewById(R.id.b_four);
        Button five = findViewById(R.id.b_five);
        Button six = findViewById(R.id.b_six);
        Button seven = findViewById(R.id.b_seven);
        Button eight = findViewById(R.id.b_eight);
        Button nine = findViewById(R.id.b_nine);
        Button ten = findViewById(R.id.b_ten);
        one.setText("");
        two.setText("");
        three.setText("");
        four.setText("");
        five.setText("");
        six.setText("");
        seven.setText("");
        eight.setText("");
        nine.setText("");
        ten.setText("");
    }

    public void startWaitingForInput(final List<String> answers)
    {
        this.runOnUiThread
        (
            new Runnable()
            {
                public void run()
                {
                    prepareForSpeechInput(answers);
                }
            }
        );
    }

    public void prepareForSpeechInput(List<String> answers)
    {
        Button connect = findViewById(R.id.connect);
        connect.setText(TAP_TO_SPEAK);
        Button one = findViewById(R.id.b_one);
        Button two = findViewById(R.id.b_two);
        Button three = findViewById(R.id.b_three);
        Button four = findViewById(R.id.b_four);
        Button five = findViewById(R.id.b_five);
        Button six = findViewById(R.id.b_six);
        Button seven = findViewById(R.id.b_seven);
        Button eight = findViewById(R.id.b_eight);
        Button nine = findViewById(R.id.b_nine);
        Button ten = findViewById(R.id.b_ten);
        int n = answers.size(), i = 1, j = 0;
        one.setText(n >= i++ ? answers.get(j++) : "");
        two.setText(n >= i++ ? answers.get(j++) : "");
        three.setText(n >= i++ ? answers.get(j++) : "");
        four.setText(n >= i++ ? answers.get(j++) : "");
        five.setText(n >= i++ ? answers.get(j++) : "");
        six.setText(n >= i++ ? answers.get(j++) : "");
        seven.setText(n >= i++ ? answers.get(j++) : "");
        eight.setText(n >= i++ ? answers.get(j++) : "");
        nine.setText(n >= i++ ? answers.get(j++) : "");
        ten.setText(n >= i++ ? answers.get(j++) : "");
    }

    // Show speech input dialog
    private void promptForSpeechInput()
    {
        if (_speechRecognizer == null)
        {
            _speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            _speechRecognizer.setRecognitionListener(this);
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Response for robot");
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000); // 30 seconds
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000); // 3 seconds
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000); // 3 seconds
        _speechRecognizer.startListening(intent);
    }

    private void sleepForMilliseconds(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (Exception ignore)
        {
        }
    }

    @Override
    public void /* SpeechRecognitionListener*/ onReadyForSpeech(Bundle params)
    {
    }

    @Override
    public void /* SpeechRecognitionListener*/ onBeginningOfSpeech()
    {
        if (RobotSpeech.DEBUG) _robotSpeech.logDebug("SpeechRecognitionListener.onBeginningOfSpeech");
    }

    @Override
    public void /* SpeechRecognitionListener*/ onRmsChanged(float rmsdB)
    {
    }

    @Override
    public void /* SpeechRecognitionListener*/ onBufferReceived(byte[] buffer)
    {
    }

    @Override
    public void /* SpeechRecognitionListener*/ onEndOfSpeech()
    {
        if (RobotSpeech.DEBUG) _robotSpeech.logDebug("SpeechRecognitionListener.onEndOfSpeech");
    }

    @Override
    public void /* SpeechRecognitionListener*/ onError(int error)
    {
        if (RobotSpeech.DEBUG) _robotSpeech.logDebug("SpeechRecognitionListener.onError(" + error + ")");
        clearButtons(WAITING_FOR_ROBOT);
        if (error == SpeechRecognizer.ERROR_NO_MATCH)
        {
            _robotSpeech.addAnswer("<NONE>");
        }
        else
        {
            _robotSpeech.addAnswer("Speech Recognition Error " + error);
        }
    }

    @Override
    public void /* SpeechRecognitionListener*/ onResults(Bundle results)
    {
        if (RobotSpeech.DEBUG) _robotSpeech.logDebug("SpeechRecognitionListener.onResults()");
        ArrayList<String> resultsList = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        clearButtons(WAITING_FOR_ROBOT);
        _robotSpeech.addAnswer(resultsList.get(0));
    }

    @Override
    public void /* SpeechRecognitionListener*/ onPartialResults(Bundle partialResults)
    {
    }

    @Override
    public void /* SpeechRecognitionListener*/ onEvent(int eventType, Bundle params)
    {
    }
}
