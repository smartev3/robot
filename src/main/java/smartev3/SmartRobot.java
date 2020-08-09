package smartev3;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.*;

import ev3dev.actuators.*;
import ev3dev.actuators.lego.motors.*;
import ev3dev.robotics.tts.*;
import ev3dev.sensors.ev3.*;
import lejos.hardware.port.*;
import lejos.hardware.sensor.*;
import lejos.robotics.*;
import org.slf4j.*;

public class SmartRobot
{
    public enum InputMode
    {
        CONSOLE,
        SONAR,
        SPEECH,
        TOUCH;
    };

    public static final float BASE_POWER_TO_SPEED_FACTOR = 10;
    public static final float HEAD_POWER_TO_SPEED_FACTOR = 10;
    public static final float MAXIMUM_HAND_SIGNAL_DISTANCE = 30;
    public static final float MAXIMUM_TRUSTED_DISTANCE = 254;

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public boolean silent = false;
    public boolean useConsole = false;
    public boolean useTelevision = false;

    public EV3MediumRegulatedMotor headMotor;
    public EV3LargeRegulatedMotor leftMotor;
    public EV3LargeRegulatedMotor rightMotor;
    public EV3GyroSensor gyroSensor;
    public EV3UltrasonicSensor sonarSensor;
    public EV3TouchSensor leftTouchSensor;
    public EV3TouchSensor rightTouchSensor;

    public Shutdown shutdown = new Shutdown();
    public Bump bump = new Bump();

    public ProximityMap proximityMap = new ProximityMap(this, 0, 0);
    public boolean discoveryMode = false;
    public Point moveStartingAt;
    public Point moveFinishedAt;

    private boolean firstQuestion = true;
    private volatile int fullTurnSteps = 0;
    private SampleProvider gyroProvider = null;
    private SampleProvider sonarProvider;
    private float[] gyroSample = new float[1];
    private float[] sonarSample = new float[1];

    private Object baseLock = new Object();
    private Object headLock = new Object();
    private Object gyroLock = new Object();
    private Object sonarLock = new Object();
    private Object touchLock = new Object();

    private Sound sound;
    private SoundThread soundThread = new SoundThread();

    private Espeak speech = new Espeak();
    private SpeechThread speechThread = new SpeechThread();
    private SpeechServer speechServer = new SpeechServer(SpeechServer.PORT);

    SmartRobot()
    {
        // Ensure that motors are stopped on program exit.
        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    public String robotName()
    {
        return "Number 5 Junior";
    }

    public InputMode inputMode()
    {
        if (useConsole)
        {
            return InputMode.CONSOLE;
        }
        else if (SpeechServer.hasClient())
        {
            return InputMode.SPEECH;
        }
        else
        {
            return InputMode.SONAR;
        }
    }

    public boolean inputModeAllowsMultiChoice()
    {
        return !inputModeIsYesNoOnly();
    }

    public boolean inputModeIsYesNoOnly()
    {
        InputMode mode = inputMode();
        return mode == InputMode.SONAR;
    }

    public Port headMotorPort()
    {
        return MotorPort.A;
    }

    public Port leftMotorPort()
    {
        return MotorPort.B;
    }

    public Port rightMotorPort()
    {
        return MotorPort.D;
    }

    public Port sonarSensorPort()
    {
        return SensorPort.S2;
    }

    public Port gyroSensorPort()
    {
        return SensorPort.S3;
    }

    public Port leftTouchPort()
    {
        return SensorPort.S1;
    }

    public Port rightTouchPort()
    {
        return SensorPort.S4;
    }

    public int lookAroundAngle()
    {
        return 120;
    }

    public int lookAroundSpeed()
    {
        return 100;
    }

    public int minimumForwardPower()
    {
        return 10;
    }

    public int maximumForwardPower()
    {
        return 90;
    }

    public int minimumTurningPower()
    {
        return 10;
    }

    public float eyesToMidpoint()
    {
        return 11.0f;
    }

    public float wheelDiameter()
    {
        return 5.6f;
    }

    public float safePassingWidth()
    {
        return 30;
    }

    public float safeStoppingDistance()
    {
        return 20;
    }

    private synchronized void connect()
    {
        connectMotors();
        connectSensors();
    }

    private synchronized void connectMotors()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting connectMotors");
        }
        int retryDelay = 20; // milliseconds
        while (true)
        {
            try
            {
                if (headMotor == null) headMotor = new EV3MediumRegulatedMotor(headMotorPort());
                if (leftMotor == null) leftMotor = new EV3LargeRegulatedMotor(leftMotorPort());
                if (rightMotor == null) rightMotor = new EV3LargeRegulatedMotor(rightMotorPort());
                break;
            }
            catch (Exception error)
            {
                logger.info(error.getMessage());
                sleepForMilliseconds(retryDelay);
                retryDelay *= 2;
                System.out.println("connectMotors retrying...");
            }
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished connectMotors");
        }
    }

    private synchronized void connectSensors()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting connectSensors");
        }
        int retryDelay = 20; // milliseconds
        while (true)
        {
            try
            {
                if (sound == null) sound = Sound.getInstance();
                if (gyroSensor == null) gyroSensor = new EV3GyroSensor(gyroSensorPort());
                if (sonarSensor == null) sonarSensor = new EV3UltrasonicSensor(sonarSensorPort());
                if (sonarProvider == null) sonarProvider = sonarSensor.getDistanceMode();
                if (leftTouchSensor == null) leftTouchSensor = new EV3TouchSensor(leftTouchPort());
                if (rightTouchSensor == null) rightTouchSensor = new EV3TouchSensor(rightTouchPort());
                break;
            }
            catch (Exception error)
            {
                logger.info(error.getMessage());
                sleepForMilliseconds(retryDelay);
                retryDelay *= 2;
                System.out.println("connectSensors retrying...");
            }
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished connectSensors");
        }
    }

    public int gyroAngle()
    {
        synchronized (gyroLock)
        {
            SampleProvider provider = gyroProvider;
            if (provider == null)
            {
                fail("Gyroscope sensor is not calibrated! Call calibrateGyroscope() before using it.");
            }
            provider.fetchSample(gyroSample, 0);
            return (int)gyroSample[0];
        }
    }

    public int headAngle()
    {
        synchronized (headLock)
        {
            return (int)headMotor.getPosition();
        }
    }

    public float headDistance()
    {
        synchronized (sonarLock)
        {
            sonarProvider.fetchSample(sonarSample, 0);
            return sonarSample[0];
        }
    }

    public boolean leftTouch()
    {
        synchronized (touchLock)
        {
            return leftTouchSensor.isPressed();
        }
    }

    public boolean rightTouch()
    {
        synchronized (touchLock)
        {
            return rightTouchSensor.isPressed();
        }
    }

    public void fail(String reason)
    {
        throw new MissionFailure(reason);
    }

    public void defaultMission()
    {
        fail("Default mission is undefined!");
    }

    public void run(String[] args)
    {
        connect(); // motors and sensors
        soundThread.start();
        speechThread.start();
        if (logger.isDebugEnabled())
        {
            SpeechServer.DEBUG = true;
        }
        speechServer.start();
        calibrateGyroscope(); // if this doesn't work, robot might need rebooting
        int optionIndex = 0;
        while (optionIndex < args.length && args[optionIndex].startsWith("-"))
        {
            if (args[optionIndex].equals("-silent"))
            {
                silent = true;
            }
            else if (args[optionIndex].equals("-console"))
            {
                useConsole = true;
            }
            else if (args[optionIndex].equals("-tv"))
            {
                useTelevision = true;
            }
            else
            {
                throw new RuntimeException("Unknown option: " + args[optionIndex]);
            }
            optionIndex++;
        }
        Throwable failure = null;
        boolean useDefault = optionIndex == args.length;        
        try
        {
            while (optionIndex < args.length || useDefault)
            {
                String methodName = useDefault ? "defaultMission" : args[optionIndex++];
                java.lang.reflect.Method method;
                try
                {
                    method = this.getClass().getMethod(methodName, new Class[0]);
                }
                catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
                logger.info("Starting " + methodName);
                method.invoke(this, new Object[0]);
                logger.info("Finished " + methodName);
                if (useDefault) break;
            }
        }
        catch (InvocationTargetException ex)
        {
            failure = ex.getTargetException();
            if (failure == null) failure = ex;
        }
        catch (Exception ex)
        {
            failure = ex;
        }
        if (failure != null)
        {
            if (failure instanceof MissionFailure)
            {
                logger.error("Mission Failed! " + failure.getMessage());
            }
            else
            {
                logger.error("Unexpected Exception!", failure);
            }
        }
        shutdown.normal();
        stopAllMotorsWithCoast();
    }

    public void pause()
    {
        System.out.print("Program paused. Press Enter to continue... ");
        readLine();
    }

    public void clearScreen()
    {
        for (int line = 1; line <= 100; line++) System.out.println();
    }

    public String readLine()
    {
        StringBuilder line = new StringBuilder();
        try
        {
            int c;
            while ((c = System.in.read()) != '\n')
            {
                if (c == -1)
                {
                    throw new IOException("end-of-input");
                }      
                line.append((char)c);          
            }
            return line.toString();
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    public void touchToStart()
    {
        clearScreen();
        System.out.println("Press one of the red touch sensors to start");
        System.out.println("or use the \"Robot Speech\" app to connect...");
        while (!(leftTouch() || rightTouch()))
        {
            // Loop until one is touched.
            if (inputMode() == InputMode.SPEECH)
            {
                break;
            }
            sleepForMilliseconds(100);
        }
        clearScreen();
    }

    public void flushSound()
    {
        soundThread.flush();
    }

    public void playTone(int frequency, int duration, int volume)
    {
        soundThread.playTone(frequency, duration, volume);
    }

    public void say(String message)
    {
        InputMode mode = inputMode();
        if (mode == InputMode.CONSOLE)
        {
            System.out.println("\n" + message);
        }
        else if (mode == InputMode.SPEECH)
        {
            speechServer.say(message);
        }
        else
        {
            synchronized (speech)
            {
                speech.setMessage(message);
                speech.say();
                // speechThread.say(message);
            }
        }
    }

    public void sayHowToGetSpeechHelp()
    {
        say("If you are unsure of the answers that I will accept for a question, please say: help.");
    }

    public boolean currentlySpeaking()
    {
        return false;
        // return speechThread.busy();
    }

    public int askUserToChoose(String question, List<String> choices)
    {
        return askUserToChoose(question, choices, new WordAliases());
    }

    public int askUserToChoose(String question, List<String> choices, WordAliases aliases)
    {
        switch (inputMode())
        {
            case CONSOLE:
                return askUserToChooseWithConsole(question, choices);
            case SONAR:
                return askUserToChooseWithSonar(question, choices);
            case SPEECH:
                return askUserToChooseWithSpeech(question, choices, aliases);
            case TOUCH:
                return askUserToChooseWithTouch(question, choices);
            default:
                throw new IllegalStateException();
        }
    }

    public int askUserToChooseWithConsole(String question, List<String> choices)
    {
        for (;;)
        {
            say(question);
            int i = 0;
            for (String choice : choices)
            {
                i++;
                System.out.println("    Answer " + i + ": " + choices.get(i - 1));
            }
            System.out.print("Enter answer: ");
            String line = System.console().readLine();
            try
            {
                int choice = Integer.parseInt(line.trim());
                if (choice < 1 || choice > choices.size())
                {
                    throw new Exception("bad choice");
                }
                return choice;
            }
            catch (Exception tryAgain)
            {
            }
        }
    }

    public int askUserToChooseWithSonar(String question, List<String> choices)
    {
        throw new RuntimeException("Not Implemented");
    }

    public int askUserToChooseWithSpeech(String question, List<String> choices, WordAliases aliases)
    {
        String answer = speechServer.ask(question, choices, aliases);
        int n = choices.size();
        for (int i = 0; i < n; i++)
        {
            if (answer.equals(choices.get(i)))
            {
                return i + 1;
            }            
        }
        throw new IllegalStateException();
    }

    public int askUserToChooseWithTouch(String question, List<String> choices)
    {
        throw new RuntimeException("Not Implemented");
    }

    public int askUserForNumber(String question, int from, int to)
    {
        if (from < 0 || to > 10)
        {
            throw new IllegalArgumentException("from = " + from + ", to = " + to);
        }
        List<String> choices = new ArrayList<String>();
        WordAliases aliases = new WordAliases();
        for (int i = from; i <= to; i++)
        {
            choices.add(String.valueOf(i));
            switch (i)
            {
                case 1: aliases.add("1", "one", "when", "win", "won"); break;
                case 2: aliases.add("2", "two", "to", "too"); break;
                case 3: aliases.add("3", "three", "free"); break;
                case 4: aliases.add("4", "four", "far", "for", "fur"); break;
                case 5: aliases.add("5", "five"); break;
                case 6: aliases.add("6", "six", "sax", "sex", "sick", "socks"); break;
                case 7: aliases.add("7", "seven"); break;
                case 8: aliases.add("8", "eight", "ate"); break;
                case 9: aliases.add("9", "nine", "known", "no nz"); break;
                case 10: aliases.add("10", "ten", "tan", "tin", "ton"); break;
            }
        }
        int choice = askUserToChoose(question, choices, aliases);
        return from + (choice - 1);
    }

    public boolean askYesNoQuestion(String question)
    {
        switch (inputMode())
        {
            case CONSOLE:
                return askYesNoQuestionWithConsole(question);
            case SONAR:
                return askYesNoQuestionWithSonar(question);
            case SPEECH:
                return askYesNoQuestionWithSpeech(question);
            case TOUCH:
                return askYesNoQuestionWithTouch(question);
            default:
                throw new IllegalStateException();
        }
    }

    public boolean askYesNoQuestionWithConsole(String question)
    {
        List<String> choices = new ArrayList<String>();
        choices.add("Yes");
        choices.add("No");
        return askUserToChoose(question, choices) == 1;
    }

    public boolean askYesNoQuestionWithSonar(String question)
    {
        int middleC = 262; // 261.626
        int middleE = 330; // 329.628
        int middleG = 392; // 391.995
        int toneTime = 125;
        int toneVolume = 100;
        int sayAgainTime = 30; // seconds
        Timer timer;
        SAY_AGAIN:
        while (true)
        {
            if (silent)
            {
                say(question);
            }
            else
            {
                speechThread.say(question);
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("Waiting to see start of hand wave (1).");
            }
            timer = new Timer();
            while (headDistance() > MAXIMUM_HAND_SIGNAL_DISTANCE)
            {
                // Waiting to see user's hand...
                if (timer.getSeconds() > sayAgainTime) continue SAY_AGAIN;
            }
            speechThread.flush();
            playTone(middleC, toneTime, toneVolume);
            if (logger.isDebugEnabled())
            {
                logger.debug("Waiting to see end of hand wave (1).");
            }
            timer = new Timer();
            while (headDistance() <= MAXIMUM_HAND_SIGNAL_DISTANCE
                || timer.getMilliseconds() < 250)
            {
                // Waiting until can't see user's hand...
                if (timer.getSeconds() > sayAgainTime) continue SAY_AGAIN;
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("Waiting to see start of hand wave (2).");
            }
            timer = new Timer();
            while (headDistance() > MAXIMUM_HAND_SIGNAL_DISTANCE)
            {
                // Waiting to see user's hand...
                if (soundThread.busy())
                {
                    timer = new Timer();
                }
                if (timer.getMilliseconds() > 2000)
                {
                    soundThread.flush();
                    say("No");
                    return false;
                }
                if (timer.getSeconds() > sayAgainTime) continue SAY_AGAIN;
            }
            playTone(middleE, toneTime, toneVolume);
            if (logger.isDebugEnabled())
            {
                logger.debug("Waiting to see end of hand wave (2).");
            }
            timer = new Timer();
            while (headDistance() <= MAXIMUM_HAND_SIGNAL_DISTANCE
                || timer.getMilliseconds() < 250)
            {
                // Waiting until can't see user's hand...
                if (timer.getSeconds() > sayAgainTime) continue SAY_AGAIN;
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("Waiting to see start of hand wave (3).");
            }
            timer = new Timer();
            while (headDistance() > MAXIMUM_HAND_SIGNAL_DISTANCE)
            {
                // Waiting to see user's hand...
                if (soundThread.busy())
                {
                    timer = new Timer();
                }
                if (timer.getMilliseconds() > 2000)
                {
                    soundThread.flush();
                    speechThread.say("That didn't look like yes or no. Please use three hand passes for yes, or one hand pass for no.");
                    continue SAY_AGAIN;
                }
                if (timer.getSeconds() > sayAgainTime) continue SAY_AGAIN;
            }
            playTone(middleG, toneTime, toneVolume);
            soundThread.flush();
            say("Yes");
            return true;
        }
    }

    public boolean askYesNoQuestionWithSpeech(String question)
    {
        List<String> choices = new ArrayList<String>(2);
        choices.add("Yes");
        choices.add("No");
        WordAliases aliases = new WordAliases().addYes().addNo();
        String answer = speechServer.ask(question, choices, aliases);
        return answer.equals("Yes");
    }

    public boolean askYesNoQuestionWithTouch(String question)
    {
        for (int pass = 1;; pass++)
        {
            say(question);
            long startedWaiting = elapsedTimeMilliseconds();
            do
            {
                if (rightTouch()) // Robot's right == user's left.
                {
                    say("Yes.");
                    sleepForSeconds(1);
                    return true;
                }
                if (leftTouch()) // Robot's left == user's right.
                {
                    say("No.");
                    sleepForSeconds(1);
                    return false;
                }
            }
            while (elapsedTimeMilliseconds() - startedWaiting < 5000);
            say("Please check out the red touch sensors in front of my wheels. When facing me, press the touch sensor on your left to answer Yes, or press the touch sensor on your right to answer No.");
            sleepForSeconds(2);
        }
    }

    public void teachUserHowToCommunicate()
    {
        switch (inputMode())
        {
            case CONSOLE:
                break;
            case SONAR:
                teachUserHowToCommunicateBySonar();
                break;
            case SPEECH:
                teachUserHowToCommunicateBySpeech();
                break;
            case TOUCH:
                teachUserHowToCommunicateByTouch();
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public void teachUserHowToCommunicateBySonar()
    {
        say("I don't have any ears, so I cannot hear you.");
        sleepForSeconds(1);
        say("I can detect hand signals using my sonar sensor, which uses ultrasonic pulses."
            + " My sonar sensor looks like a pair of red eyes.");
        sleepForSeconds(1);
        say("Each time I ask you a question, you can answer No"
            + " by passing your hand once from side to side about 15 centimetres in front of my eyes."
            + " You can answer Yes by passing your hand three times from side to side.");
        sleepForSeconds(1);
        for (int pass = 1;; pass++)
        {
            String promptForNo = "Let's practice you saying No.";
            if (pass > 1)
            {
                promptForNo += " Please pass your hand once from side to side about 15 centimetres in front of my eyes.";
            }
            if (!askYesNoQuestion(promptForNo))
            {
                break;
            }
            say("Sorry, that looked like you were saying Yes. We'll try again.");
        }
        for (int pass = 1;; pass++)
        {
            String promptForYes = "Let's practice you saying Yes.";
            if (pass > 1)
            {
                promptForYes += " Please pass your hand three times from side to side about 15 centimetres in front of my eyes to say No.";
            }
            if (askYesNoQuestion(promptForYes))
            {
                break;
            }
            say("Sorry, that looked like you were saying No. We'll try again.");
        }
        say("OK, now we can communicate.");
        sleepForSeconds(2);
    }

    public void teachUserHowToCommunicateBySpeech()
    {
        do
        {
            say("We can communicate by me asking questions, and you answering them.");
            say("When you hear a question, you can click the Tap to Speak button in the Robot Speech app and wait for the short beep sound before answering.");
            say("Or you can use the labelled answer buttons in the Robot Speech app.");
            sayHowToGetSpeechHelp();
        }
        while (!askYesNoQuestion("Do you understand how we can communicate?"));
    }

    public void teachUserHowToCommunicateByTouch()
    {
    }

    public void calibrate()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting calibrate");
        }
        calibrateGyroscope();
        calibrateMotors();
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished calibrate");
        }
    }

    public void calibrateGyroscope()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting calibrateGyroscope");
        }
        Timeout timeout = new TimeoutInSeconds("gyroscope calibration", 30);
        synchronized (gyroLock)
        {
            gyroProvider = null;
        }
        for (int pass = 1;; pass++)
        {
            shutdown.check();
            sleepForMilliseconds(200 * pass);
            gyroSensor.getRateMode();
            sleepForMilliseconds(100 * pass);
            SampleProvider angleMode = gyroSensor.getAngleMode();
            sleepForMilliseconds(100 * pass);
            synchronized (gyroLock)
            {
                gyroProvider = angleMode;
                int angle = gyroAngle();
                if (angle == 0) break;
                gyroProvider = null;
            }
            timeout.check();
            if (logger.isDebugEnabled())
            {
                logger.debug("........ calibrateGyroscope (retrying)");
            }
            if (pass == 5)
            {
                say("I'm having some trouble with my gyroscope calibration. If it doesn't work, I might need to be powered off and restarted!");
            }
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished calibrateGyroscope");
        }
    }

    public void calibrateMotors()
    {
        int startAngle = gyroAngle();
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting calibrateMotors (angle: " + startAngle + " degrees)");
        }
        proximityMap = new ProximityMap(this, -180, 180);
        Timeout timeout = new TimeoutInSeconds("motor calibration", 20);
        int targetSpeed = 100;
        int targetSteps = 500; // Expect this to be probably less than 360 degree turn
        leftMotor.setSpeed(targetSpeed);
        rightMotor.setSpeed(targetSpeed);
        leftMotor.rotateTo((int)leftMotor.getPosition() + targetSteps, true);
        rightMotor.rotateTo((int)rightMotor.getPosition() - targetSteps, true);
        waitUntilBaseStopsMoving(timeout, true, startAngle);
        int middleAngle = gyroAngle();
        int gyroDiff = Math.abs(middleAngle - startAngle);
        double fullTurnFactor = 360.0 / gyroDiff;
        fullTurnSteps = (int)(targetSteps * fullTurnFactor);
        if (logger.isDebugEnabled())
        {
            logger.debug("........ calibrateMotors (full turn: " + fullTurnSteps + " steps)");
        }
        if (fullTurnSteps < 600 || fullTurnSteps > 1200)
        {
            fail("Expected full turn steps in range 600 to 1200, but calibration found " + fullTurnSteps + "!");
        }
        int remainingSteps = fullTurnSteps - targetSteps;
        leftMotor.rotateTo((int)leftMotor.getPosition() + remainingSteps, true);
        rightMotor.rotateTo((int)rightMotor.getPosition() - remainingSteps, true);
        waitUntilBaseStopsMoving(timeout, true, startAngle);
        int finishAngle = gyroAngle();
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished calibrateMotors (angle: " + finishAngle + " degrees)");
        }
    }

    public void lookAround()
    {
        lookAround(lookAroundAngle(), 0);
    }

    public void lookAround(int targetAngle, int repeatPass)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting lookAround (head position: " + headAngle() + " degrees)");
        }
        if (repeatPass > 0)
        {
            proximityMap = new ProximityMap(this, -targetAngle, targetAngle);
        }
        for (int pass = 1; pass <= 1; pass++)
        {
            shutdown.check();
            if (logger.isDebugEnabled())
            {
                logger.debug("........ lookAround (turning head to left: " + -targetAngle + " degrees)");
            }
            headMotor.setSpeed(lookAroundSpeed());
            headMotor.rotateTo(-targetAngle, true);
            Timeout leftTimeout = new TimeoutInSeconds("turning head to left", 5);
            waitUntilHeadStopsMoving(leftTimeout);
            if (logger.isDebugEnabled())
            {
                logger.debug("........ lookAround (turning head to right: " + targetAngle + " degrees)");
            }
            headMotor.rotateTo(targetAngle, true);
            Timeout rightTimeout = new TimeoutInSeconds("turning head to left", 5);
            waitUntilHeadStopsMoving(rightTimeout);
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("........ lookAround (turning head to center)");
        }
        headMotor.rotateTo(0, true);
        Timeout centerTimeout = new TimeoutInSeconds("turning head to center", 5);
        waitUntilHeadStopsMoving(centerTimeout);
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished lookAround (head position: " + headAngle() + " degrees)");
        }
    }

    public void turnDegrees(int angle)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting turnDegrees (target: " + angle + " degrees)");
        }
        Timeout timeout = new TimeoutInSeconds("turning base", 10);
        int startGyro = gyroAngle();
        if (angle != 0)
        {
            while (angle < -180) angle += 360;
            while (angle > 180) angle -= 360;
            int power = minimumTurningPower();
            int speed = (int)(power * BASE_POWER_TO_SPEED_FACTOR);
            int steps = (int)(fullTurnSteps * (angle / 360.0));
            int leftTarget = (int)leftMotor.getPosition() + steps;
            int rightTarget = (int)rightMotor.getPosition() - steps;
            leftMotor.setSpeed(speed);
            rightMotor.setSpeed(speed);
            leftMotor.rotateTo(leftTarget, true);
            rightMotor.rotateTo(rightTarget, true);
            sleepForMilliseconds(200); // give motors a chance to start
            waitUntilBaseStopsMoving(timeout);
        }
        if (logger.isDebugEnabled())
        {
            int finishGyro = gyroAngle();
            int deltaGyro = finishGyro - startGyro;
            logger.debug("Finished turnDegrees (turned: " + deltaGyro + " degrees)");
        }
    }

    public void turnToBearing(double bearing)
    {
        int target = (int)bearing % 360;
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting turnToBearing (target: " + target + " degrees)");
        }
        int delta = (target - gyroAngle()) % 360;
        turnDegrees(delta);
        if (logger.isDebugEnabled())
        {
            int result = gyroAngle();
            logger.debug("Finished turnToBearing (result: " + result + " degrees)");
        }
    }

    public float moveForward(float distance)
    {
        String activity = "moving forward";
        int timeoutSeconds = 20;
        Timeout timeout = new TimeoutInSeconds(activity, timeoutSeconds);
        int powerPassIncrement = 2;
        float wheelCircumference = (float)(wheelDiameter() * Math.PI);
        float wheelRotations = distance / wheelCircumference;
        int wheelSteps = (int)(wheelRotations * 360);
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting moveForward (distance: " + formatFixed1(distance)
                + " cm, steps: " + wheelSteps + ")");
        }
        long startTime = elapsedTimeMilliseconds();
        moveFinishedAt = moveStartingAt;
        if (distance > 0)
        {
            int leftStart = (int)leftMotor.getPosition();
            int rightStart = (int)rightMotor.getPosition();
            int lastLeftSteps = leftStart;
            int lastRightSteps = rightStart;
            int startAngle = gyroAngle();
            int rampUpPower = minimumForwardPower();
            int targetPower = maximumForwardPower();
            int addLeftPower = 0;
            int addRightPower = 0;
            int lastLeftPower = Integer.MIN_VALUE;
            int lastRightPower = Integer.MIN_VALUE;
            float halfSafePassingWidth = safePassingWidth() / 2;
            List<ObstacleAhead> obstaclesAhead = new ArrayList<ObstacleAhead>();
            float remainingDistance = distance;
            float stoppingDistance = 10; // cm
            float checkAhead = 80;
            float checkAside = safePassingWidth() / 2;
            int checkAngle = (int)MathHelper.radiansToDegrees(Math.atan(checkAside / checkAhead));
            HeadTurner headTurner = new HeadTurner(checkAngle, 5);
            long lastTime = elapsedTimeMilliseconds();
            for (int pass = 1;; pass++)
            {
                shutdown.check();
                bump.check();
                int leftSteps = (int)leftMotor.getPosition();
                int rightSteps = (int)rightMotor.getPosition();
                int deltaLeftSteps = Math.max(0, leftSteps - lastLeftSteps);
                int deltaRightSteps = Math.max(0, rightSteps - lastRightSteps);
                if (pass >= 3)
                {
                    if (deltaLeftSteps == 0 && deltaRightSteps == 0)
                    {
                        fail("Both base motors have stalled!");
                    }
                    if (deltaLeftSteps == 0)
                    {
                        fail("Left base motor has stalled!");
                    }
                    if (deltaRightSteps == 0)
                    {
                        fail("Right base motor has stalled!");
                    }
                }
                lastLeftSteps = leftSteps;
                lastRightSteps = rightSteps;
                int deltaSteps = Math.min(deltaLeftSteps, deltaRightSteps);
                float deltaRotations = deltaSteps / 360f;
                float deltaDistance = deltaRotations * wheelCircumference;
                remainingDistance -= deltaDistance;
                for (ObstacleAhead obstacle : obstaclesAhead)
                {
                    // We are now 'deltaDistance' closer to the obstacle
                    // than we were on the previous loop iteration.
                    obstacle.distance = Math.max(0, obstacle.distance - deltaDistance);
                }
                if (pass == 1)
                {
                    headTurner.startScanning();
                }
                else
                {
                    headTurner.sensorTick();
                }
                // See https://en.wikipedia.org/wiki/Trigonometric_functions#Sine,_cosine_and_tangent
                int headAngle = headTurner.currentAngle;
                int absHeadAngle = Math.abs(headAngle);
                double headRadians = MathHelper.degreesToRadians(absHeadAngle);
                float hypotenuse = headTurner.obstacleDistance;
                float adjacent = (float)(Math.cos(headRadians) * hypotenuse); // distance ahead
                if (hypotenuse <= checkAhead)
                {
                    obstaclesAhead.add(new ObstacleAhead(adjacent));
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("........ moveForward (obstacle ahead: " + formatFixed1(adjacent) + " cm)");
                    }
                    checkAhead = Math.min(safeStoppingDistance(), hypotenuse);
                    checkAngle = (int)MathHelper.radiansToDegrees(Math.atan(checkAside / checkAhead));
                    headTurner.changeAngle(checkAngle);
                }
                float clearDistance = Float.POSITIVE_INFINITY;
                for (ObstacleAhead obstacle : obstaclesAhead)
                {
                    clearDistance = Math.min(clearDistance, obstacle.distance);
                }
                float safeDistance = Float.isInfinite(clearDistance)
                    ? clearDistance
                    : clearDistance - safeStoppingDistance();
                if (logger.isDebugEnabled())
                {
                    logger.debug("........ moveForward (remaining distance: "
                        + formatFixed1(remainingDistance) + " cm"
                        + (Float.isInfinite(clearDistance) ? ""
                            : ", clear distance: " + formatFixed1(clearDistance) + " cm")
                        + (Float.isInfinite(safeDistance) ? ""
                            : ", safe distance: " + formatFixed1(safeDistance) + " cm")
                        + ")");
                }
                int newAngle = gyroAngle();
                if (moveFinishedAt != null)
                {
                    moveFinishedAt = moveFinishedAt.move(newAngle, deltaDistance);
                }
                if (safeDistance <= 0)
                {
                    stoppingDistance = Math.max(0, stoppingDistance + safeDistance);
                    break;
                }
                if (remainingDistance <= stoppingDistance)
                {
                    stoppingDistance = Math.max(0, remainingDistance);
                    break;
                }
                float minRemainingSafe = Math.min(remainingDistance, safeDistance);
                if (minRemainingSafe <= 50)
                {
                    // Slow down when approaching target.
                    int newTargetPower = Math.min(targetPower, Math.max(minimumForwardPower(), (int)minRemainingSafe));
                    float newPowerFactor = newTargetPower / targetPower;
                    addLeftPower = (int)(addLeftPower * newPowerFactor);
                    addRightPower = (int)(addRightPower * newPowerFactor);
                    targetPower = newTargetPower;
                }
                int currentPower = Math.min(rampUpPower, targetPower);
                rampUpPower = Math.min(rampUpPower + 10, targetPower);
                int angleDelta = newAngle - startAngle;
                if (angleDelta < 0)
                {
                    // Veering to left, need to straighten up.
                    addRightPower = 0;
                    addLeftPower++;
                }
                if (angleDelta > 0)
                {
                    // Veering to right, need to straighten up.
                    addLeftPower = 0;
                    addRightPower++;
                }
                int leftPower = Math.max(0, Math.min(100 - addRightPower, currentPower + addLeftPower));
                int rightPower = Math.max(0, Math.min(100 - addLeftPower, currentPower + addRightPower));
                if (logger.isDebugEnabled())
                {
                    logger.debug("........ moveForward (gyroscope delta: " + angleDelta
                        + " degrees, target power: " + targetPower
                        + "%, left power: " + leftPower
                        + "%, right power: " + rightPower + "%)");
                }
                if (leftPower != lastLeftPower)
                {
                    int leftSpeed = (int)(leftPower * BASE_POWER_TO_SPEED_FACTOR);
                    leftMotor.setSpeed(leftSpeed);
                    leftMotor.forward();
                    lastLeftPower = leftPower;
                }
                if (rightPower != lastRightPower)
                {
                    int rightSpeed = (int)(rightPower * BASE_POWER_TO_SPEED_FACTOR);
                    rightMotor.setSpeed(rightSpeed);
                    rightMotor.forward();
                    lastRightPower = rightPower;
                }
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("........ moveForward (stopping distance: "
                    + formatFixed1(stoppingDistance) + " cm)");
            }
            int stopPower = minimumForwardPower();
            int stopSteps = (int)(360 * (stoppingDistance / wheelCircumference));
            int stopSpeed = (int)(stopPower * BASE_POWER_TO_SPEED_FACTOR);
            if (stopSteps < 5)
            {
                leftMotor.stop();
                rightMotor.stop();
            }
            else
            {
                leftMotor.brake();
                rightMotor.brake();
                leftMotor.setSpeed(stopSpeed);
                rightMotor.setSpeed(stopSpeed);
                leftMotor.rotateTo((int)leftMotor.getPosition() + stopSteps, true);
                rightMotor.rotateTo((int)rightMotor.getPosition() + stopSteps, true);
            }
            headTurner.stopScanning();
            Timeout stopTimeout = new TimeoutInSeconds("forward movement", 5);
            waitUntilBaseStopsMoving(stopTimeout);
            leftMotor.coast();
            leftMotor.stop();
            rightMotor.coast();
            rightMotor.stop();
            headTurner.waitUntilStopped();
            float finalSteps = Math.min((int)leftMotor.getPosition() - leftStart,
                (int)rightMotor.getPosition() - rightStart);
            float finalRotations = finalSteps / 360.0f;
            distance = wheelCircumference * finalRotations;
            if (moveFinishedAt != null)
            {
                moveFinishedAt = moveFinishedAt.move(gyroAngle(), stoppingDistance);
            }
        }
        if (logger.isDebugEnabled())
        {
            long finishTime = elapsedTimeMilliseconds();
            long movingTime = finishTime - startTime;
            String showDirect = "";
            if (moveStartingAt != null && moveFinishedAt != null)
            {
                double directLine = moveStartingAt.distanceTo(moveFinishedAt);
                showDirect = ", straight: " + formatFixed1(directLine) + " cm";
            }
            logger.debug("Finished moveForward (distance: " + formatFixed1(distance)
                + " cm" + showDirect + ", time: " + movingTime + " ms)");
        }
        return distance;
    }

    public float stepForward(float distance)
    {
        String activity = "stepping forward backward";
        int timeoutSeconds = (int)(20 * Math.ceil(distance / 100)); // 20 seconds per metre
        Timeout timeout = new TimeoutInSeconds(activity, timeoutSeconds);
        int powerPassIncrement = 2;
        float wheelCircumference = (float)(wheelDiameter() * Math.PI);
        float wheelRotations = distance / wheelCircumference;
        int wheelSteps = (int)(wheelRotations * 360);
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting stepForward (distance: " + formatFixed1(distance)
                + " cm, steps: " + wheelSteps + ")");
        }
        if (distance > 0)
        {
            int power = 20;
            int speed = (int)(power * BASE_POWER_TO_SPEED_FACTOR);
            leftMotor.setSpeed(speed);
            rightMotor.setSpeed(speed);
            leftMotor.rotateTo((int)leftMotor.getPosition() + wheelSteps, true);
            rightMotor.rotateTo((int)rightMotor.getPosition() + wheelSteps, true);
            sleepForMilliseconds(200); // give motors a chance to start
            waitUntilBaseStopsMoving(timeout);
            leftMotor.coast();
            leftMotor.stop();
            rightMotor.coast();
            rightMotor.stop();
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished stepForward");
        }
        return distance;
    }

    public float stepBackward(float distance)
    {
        String activity = "stepping backward";
        int timeoutSeconds = (int)(20 * Math.ceil(distance / 100)); // 20 seconds per metre
        Timeout timeout = new TimeoutInSeconds(activity, timeoutSeconds);
        int powerPassIncrement = 2;
        float wheelCircumference = (float)(wheelDiameter() * Math.PI);
        float wheelRotations = distance / wheelCircumference;
        int wheelSteps = (int)(wheelRotations * 360);
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting stepBackward (distance: " + formatFixed1(distance)
                + " cm, steps: " + wheelSteps + ")");
        }
        if (distance > 0)
        {
            int power = 20;
            int speed = (int)(power * BASE_POWER_TO_SPEED_FACTOR);
            leftMotor.setSpeed(speed);
            rightMotor.setSpeed(speed);
            leftMotor.rotateTo((int)leftMotor.getPosition() - wheelSteps, true);
            rightMotor.rotateTo((int)rightMotor.getPosition() - wheelSteps, true);
            sleepForMilliseconds(200); // give motors a chance to start
            waitUntilBaseStopsMoving(timeout);
            leftMotor.coast();
            leftMotor.stop();
            rightMotor.coast();
            rightMotor.stop();
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished stepBackward");
        }
        return distance;
    }

    public int randomChoice(int from, int to)
    {
        return (int)(Math.random() * (to - from + 1)) + from;
    }

    public void sleepForMilliseconds(int milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (Exception ignore)
        {
        }
    }

    public void stopAllMotorsWithCoast()
    {
        headMotor.coast();
        headMotor.stop();
        leftMotor.coast();
        leftMotor.stop();
        rightMotor.coast();
        rightMotor.stop();
    }

    public void waitUntilBaseStopsMoving(Timeout timeout)
    {
        waitUntilBaseStopsMoving(timeout, false, 0);
    }

    public void waitUntilBaseStopsMoving(Timeout timeout, boolean updateProximity, int startingAngle)
    {
        for (int pass = 1;; pass++)
        {
            shutdown.check();
            if (updateProximity)
            {
                int angle = gyroAngle() - startingAngle;
                float distance = headDistance();
                if (distance > MAXIMUM_TRUSTED_DISTANCE)
                {
                    distance = Float.NaN;
                }
                if (discoveryMode && !Float.isNaN(distance))
                {
                    distance += eyesToMidpoint();
                }
                while (angle < 0 && angle < proximityMap.minimumAngle)
                {
                    angle += 360;
                }
                while (angle > 0 && angle > proximityMap.maximumAngle)
                {
                    angle -= 360;
                }
                if (!Float.isNaN(distance))
                {
                    proximityMap.setDistanceAtAngle(angle, distance);
                }
            }
            if (!updateProximity || pass % 10 == 0)
            {
                if (!leftMotor.isMoving() && !rightMotor.isMoving())
                {
                    break;
                }
            }
            timeout.check();
        }
    }

    public void waitUntilHeadStopsMoving(Timeout timeout)
    {
        for (int pass = 1;; pass++)
        {
            shutdown.check();
            float distance = headDistance();
            if (distance > MAXIMUM_TRUSTED_DISTANCE)
            {
                distance = Float.NaN;
            }
            int angle = headAngle();
            if (discoveryMode && !Float.isNaN(distance))
            {
                float adjust = Math.abs(angle) / 15; // e.g. 6 for angle of +/- 90
                // As eyes in head gets closer to turning midpoint
                // the obstacle distance is also closer to midpoint.
                distance += eyesToMidpoint() - adjust;
            }
            if (!Float.isNaN(distance))
            {
                proximityMap.setDistanceAtAngle(angle, distance);
            }
            if (pass % 5 == 0)
            {
                // Don't access headMotor.isMoving too frequently
                // as it will reduce the number of angle/distance
                // measurements.
                synchronized (headLock)
                {
                    if (!headMotor.isMoving())
                    {
                        break;
                    }
                }
            }
            timeout.check();
        }
    }

    public static String formatFixed1(double x)
    {
        return String.format("%.1f", x);
    }

    public static String formatFixed2(double x)
    {
        return String.format("%.2f", x);
    }

    public long elapsedTimeMilliseconds()
    {
        return System.nanoTime() / 1000000;
    }

    public void sleepForSeconds(int seconds)
    {
        sleepForMilliseconds(seconds * 1000);
    }

    public class Bump
    {
        private int count = 0;

        public void check()
        {
            int n = ++count, m = n % 20;
            if (m == 0)
            {
                if (leftTouch())
                {
                    fail("My left touch sensor bumped into something!");
                }
            }
            else if (m == 10)
            {
                if (rightTouch())
                {
                    fail("My right touch sensor bumped into something!");
                }
            }
        }
    }

    public class Timer
    {
        private long started;

        public Timer()
        {
            started = elapsedTimeMilliseconds();
        }

        public long getMilliseconds()
        {
            return elapsedTimeMilliseconds() - started;
        }

        public long getSeconds()
        {
            return getMilliseconds() / 1000;
        }
    }

    public abstract class Timeout
    {
        private String activity;
        private long started;
        private int timeout;

        public Timeout(String activity, int milliseconds)
        {
            this.activity = activity;
            this.started = elapsedTimeMilliseconds();
            this.timeout = milliseconds;
        }

        public void check()
        {
            long now = elapsedTimeMilliseconds();
            long elapsed = now - started;
            if (elapsed > timeout)
            {
                fail("Gave up waiting for " + activity + " to complete after " + elapsed + " milliseconds!");
            }
        }
    }

    public class TimeoutInMilliseconds extends Timeout
    {
        public TimeoutInMilliseconds(String activity, int milliseconds)
        {
            super(activity, milliseconds);
        }
    }

    public class TimeoutInSeconds extends Timeout
    {
        public TimeoutInSeconds(String activity, int seconds)
        {
            super(activity, seconds * 1000);
        }
    }

    private class HeadTurner
    {
        public int currentAngle;
        public float obstacleDistance;

        private boolean startCalled = false;
        private boolean stopCalled = false;
        private int targetAngle;
        private int nextTarget;
        private Timeout turnTimeout;
        private boolean firstFullScan = true;
        private long lastFullScanStarted = -1;
        private long minimumFullScanTime = 0;

        public HeadTurner(int angle, int power)
        {
            if (angle < 5 || angle > 120)
            {
                fail("HeadTurner angle should be in range 5 to 120 degrees (found " + angle + ")!");
            }
            if (power < 1 || power > 100)
            {
                fail("HeadTurner power should be in range 1 to 100 percent (found " + power + ")!");
            }
            targetAngle = -angle; // Start towards leftmost position
            int speed = Math.max(power * 10, 100); // ensure speed >= 100
            headMotor.brake();
            headMotor.setSpeed(speed);
        }

        public void startScanning()
        {
            currentAngle = headAngle();
            obstacleDistance = headDistance();
            int steps = currentAngle - targetAngle;
            if (steps < 0) targetAngle = -targetAngle; // Start towards rightmost position
            setTurnTimeout();
            headMotor.rotateTo(targetAngle, true);
            startCalled = true;
        }

        public void changeAngle(int angle)
        {
            if (angle < 5 || angle > 120)
            {
                fail("HeadTurner angle should be in range 5 to 120 degrees (found " + angle + ")!");
            }
            nextTarget = angle;
        }

        public void sensorTick()
        {
            if (!startCalled)
            {
                fail("HeadTurner: Please call startScanning before calling sensorTick!");
            }
            if (stopCalled)
            {
                fail("HeadTurner: Please do not call sensorTick after calling stopScanning!");
            }
            if (targetAngle == 0)
            {
                return;
            }
            currentAngle = headAngle(); // sample motor sensor
            obstacleDistance = headDistance(); // sample ultrasonic sensor
            boolean checkMoving = true;
            boolean closeEnough = Math.abs(currentAngle - targetAngle) <= 3;
            if (checkMoving && lastFullScanStarted != -1)
            {
                long timeNow = elapsedTimeMilliseconds();
                long elapsed = timeNow - lastFullScanStarted;
                // Don't check motor too often as it reduces the
                // rate at which we can sample angle and distance.
                checkMoving = elapsed >= minimumFullScanTime;
            }
            if (checkMoving && !closeEnough && !headMotor.isMoving())
            {
                closeEnough = true;
            }
            if (closeEnough)
            {
                long timeNow = elapsedTimeMilliseconds();
                if (lastFullScanStarted != -1)
                {
                    long elapsed = timeNow - lastFullScanStarted;
                    minimumFullScanTime = firstFullScan ? elapsed
                        : Math.min(elapsed, minimumFullScanTime);
                }
                if (nextTarget == 0) nextTarget = Math.abs(targetAngle);
                targetAngle = (int)-Math.signum(targetAngle) * nextTarget;
                nextTarget = 0;
                setTurnTimeout();
                lastFullScanStarted = timeNow;
                headMotor.rotateTo(targetAngle, true);
            }
            else
            {
                turnTimeout.check();
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("........ headTurning (current angle: " + currentAngle
                    + " degrees, target angle: " + targetAngle
                    + " degrees, obstacle distance: "
                    + formatFixed1(obstacleDistance) + " cm)");
            }
        }

        public void stopScanning()
        {
            targetAngle = 0;
            setTurnTimeout();
            headMotor.rotateTo(0, true);
            stopCalled = true;
        }

        public void waitUntilStopped()
        {
            if (!stopCalled)
            {
                fail("HeadTurner: Please call stopScanning before calling waitUntilStopped!");
            }
            while (headMotor.isMoving())
            {
                shutdown.check();
                turnTimeout.check();
                sleepForMilliseconds(10);
            }
            headMotor.coast();
            headMotor.stop();
        }

        private void setTurnTimeout()
        {
            turnTimeout = new TimeoutInSeconds("turning head to " + targetAngle + " degrees", 5);
        }
    }

    public class Shutdown extends Thread
    {
        private volatile boolean abnormalShutdown = false;
        private volatile boolean normalShutdown = false;

        public void check()
        {
            if (abnormalShutdown)
            {
                fail("Terminating " + Thread.currentThread().getName() + " thread due to process shutdown.");
            }
        }

        public void normal()
        {
            System.out.println("Stopping robot...");
            normalShutdown = true;
        }

        public void run()
        {
            // If called from JVM shutdown hook.
            speechServer.end();
            if (normalShutdown) return;
            System.out.println("\nStopping robot...");
            abnormalShutdown = true;
            sleepForSeconds(1);
            leftMotor.coast();
            leftMotor.stop();
            rightMotor.coast();
            rightMotor.stop();
            headMotor.brake();
            headMotor.stop();
            headMotor.setSpeed(100);
            headMotor.rotateTo(0, true);
            sleepForSeconds(2);
            headMotor.coast();
            headMotor.stop();
            sleepForSeconds(1);
        }
    }

    private static class ObstacleAhead
    {
        float distance;

        ObstacleAhead(float distance)
        {
            this.distance = distance;
        }
    }

    public class MissionFailure extends RuntimeException
    {
        MissionFailure(String message)
        {
            super(message);
        }
    }

    private class SoundThread extends Thread
    {
        private LinkedBlockingQueue<Runnable> _actions = new LinkedBlockingQueue<Runnable>();

        private volatile boolean _busy;

        SoundThread()
        {
            setDaemon(true);
        }

        public boolean busy()
        {
            return _busy;
        }

        public void flush()
        {
            while (_actions.size() != 0)
            {
                sleepForMilliseconds(10);
            }
        }

        public void playTone(int frequency, int duration, int volume)
        {
            try
            {
                _actions.put(new Runnable() { public void run() { sound.playTone(frequency, duration, volume); } });
                _busy = true;
            }
            catch (InterruptedException unexpected)
            {
                throw new RuntimeException(unexpected);
            }
        }

        public void run()
        {
            while (true)
            {
                try
                {
                    Runnable action = _actions.take();
                    action.run();
                    if (_actions.size() == 0)
                    {
                        _busy = false;
                    }
                }
                catch (InterruptedException unexpected)
                {
                    throw new RuntimeException(unexpected);
                }
            }
        }
    }

    private class SpeechThread extends Thread
    {
        private LinkedBlockingQueue<String> _messages = new LinkedBlockingQueue<String>();

        private volatile boolean _busy;

        SpeechThread()
        {
            setDaemon(true);
        }

        public boolean busy()
        {
            return _busy;
        }

        public void flush()
        {
            while (_messages.size() != 0)
            {
                sleepForMilliseconds(10);
            }
        }

        public void say(String message)
        {
            try
            {
                _messages.put(message);
                _busy = true;
            }
            catch (InterruptedException unexpected)
            {
                throw new RuntimeException(unexpected);
            }
        }

        public void run()
        {
            while (true)
            {
                try
                {
                    String message = _messages.take();
                    synchronized (speech)
                    {
                        speech.setMessage(message);
                        speech.say();
                    }
                    if (_messages.size() == 0)
                    {
                        _busy = false;
                    }
                }
                catch (InterruptedException unexpected)
                {
                    throw new RuntimeException(unexpected);
                }
            }
        }
    }
}
