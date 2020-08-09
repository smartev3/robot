package smartev3;

import java.io.*;
import java.util.*;

import javax.swing.text.DefaultStyledDocument.ElementSpec;

public class MyRobot extends SmartRobot
{
    public static void main(String[] args)
    {
        new MyRobot().run(args);
    }

    public void defaultMission()
    {
        mainMenu();
    }

    public void number5()
    {
        say("Number 5 is alive!");
        sleepForSeconds(5);
        say("Malfunction. Need input.");
    }

    public void mainMenu()
    {
        touchToStart();
        sayHello();
        for (;;)
        {
            say("I can tell jokes, play games, and execute missions.");
            if (askYesNoQuestion("Would you like to hear a joke?"))
            {
                tellJokes();
            }
            if (askYesNoQuestion("Would you like to play a game?"))
            {
                playGames();
            }
            if (askYesNoQuestion("Would you like to see me go on a mission?"))
            {
                missionMenu();
            }
            if (!askYesNoQuestion("Would you like to hear the options again?"))
            {
                hibernate("OK, I'll go to sleep now.", "Hello again. That was a short sleep.", "Hello!");
            }
        }
    }

    public void sayHello()
    {
        say("Hello, my name is " + robotName() + ".");
        sleepForSeconds(2);
        teachUserHowToCommunicate();
    }

    public void tellJokes()
    {
        for (int pass = 1;; pass++)
        {
            if (pass > 1 && !askYesNoQuestion("Would you like to hear another joke?"))
            {
                return;
            }
            say(CleanJokes.randomJoke());
            sleepForSeconds(4);
        }
    }

    public void playGames()
    {
        for (int pass = 1;; pass++)
        {
            if (pass > 1 && !askYesNoQuestion("Would you like to play another game?"))
            {
                return;
            }
            if (askYesNoQuestion("Would you like to play Noughts and Crosses?"))
            {
                playNoughtsAndCrosses();
            }
            if (inputModeAllowsMultiChoice()
                && askYesNoQuestion("Would you like to play Twenty Questions"))
            {
                playTwentyQuestions();
            }
            if (askYesNoQuestion("Would you like to play chess?"))
            {
                playChess();
            }
            if (askYesNoQuestion("Would you like to play Global Thermonuclear War?"))
            {
                if (askYesNoQuestion("Wouldn't you prefer a nice game of chess?"))
                {
                    playChess();
                }
                else
                {
                    playNuclearWar();
                }
            }
        }
    }

    public void playChess()
    {
        say("I don't know how to play chess, but I have a friend who knows how to play.");
        say("You can play chess with my friend, the computer at chess dot com.");
        hibernate("I'll have a rest while you're playing chess.",
            "Hello again. That was a short game of chess.",
            "Hello again. I hope you had a nice game of chess.");
    }

    public void playNuclearWar()
    {
        // See https://en.wikipedia.org/wiki/WarGames
        say("OK, we'll play America versus Russia.");
        if (inputModeIsYesNoOnly())
        {
            askYesNoQuestion("Would you like to play America?");
        }
        else
        {
            List<String> choices = new ArrayList<String>();
            choices.add("America");
            choices.add("Russia");
            askUserToChoose("Would you like to play America or Russia?", choices);
        }
        sleepForSeconds(2);
        say("Well, I don't really want to blow stuff up, so I'll run a simulation.");
        for (int pass = 1; pass <= 15; pass++)
        {
            int frequency = randomChoice(131, 523); // C to C, 2 octaves (either side of "middle C")
            playTone(frequency, 100 /* milliseconds */, 100);
        }
        flushSound();
        sleepForSeconds(4);
        say("A strange game. The only winning move is not to play.");
        if (askYesNoQuestion("How about a nice game of chess?"))
        {
            playChess();
        }
    }

    public void playNoughtsAndCrosses()
    {
        NoughtsAndCrosses game = new NoughtsAndCrosses(this);
        game.play();
    }

    public void playTwentyQuestions()
    {
        TwentyQuestions game = new TwentyQuestions(this);
        game.play();
    }

    public void missionMenu()
    {
        while (!askYesNoQuestion("I need to be in a safe position on the floor to execute missions. Am I in a safe position on the floor?"))
        {
            // Ask again until in a safe place.
        }
        for (int pass = 1;; pass++)
        {
            String mission = "unknown";
            try
            {
                if (pass > 1 && !askYesNoQuestion("Shall I go on another mission?"))
                {
                    return;
                }
                if (inputModeAllowsMultiChoice()
                    && askYesNoQuestion("Shall I go on the simple moves mission?"))
                {
                    mission = "simple moves";
                    simpleMoves();
                }
                else if (askYesNoQuestion("Shall I go on the avoidance mission where I move around, avoiding obstacles?"))
                {
                    mission = "avoidance";
                    avoidanceMission();
                }
                /*
                else if (askYesNoQuestion("Shall I go on the discovery mission where I move around, mapping the environment?"))
                {
                    mission = "discovery";
                    say("Sorry, the discovery mission is not implemented yet!");
                }
                */
            }
            catch (MissionFailure failure)
            {
                stopAllMotorsWithCoast();
                say("Help! My " + mission + " mission failed! " + failure.getMessage());
                sleepForSeconds(5);
                String pleaseMoveMe = "Please move me to a safe position on the floor with clear space around me.";
                say(pleaseMoveMe);
                sleepForSeconds(5);
                while (!askYesNoQuestion("Am I in a safe position on the floor now?"))
                {
                    say(pleaseMoveMe);
                    sleepForSeconds(5);
                }
            }
        }
    }

    public void simpleMoves()
    {
        SimpleMoves mission = new SimpleMoves(this);
        mission.run();
    }

    public void avoidanceMission()
    {
        AvoidanceMission mission = new AvoidanceMission(this, 0);
        mission.run();
    }

    public void avoidanceMissionWithPause()
    {
        AvoidanceMission mission = new AvoidanceMission(this, 1);
        mission.run();
    }

    public void showBearing()
    {
        calibrate();
        for (;;)
        {
            System.out.println("gyroAngle = " + gyroAngle());
            askYesNoQuestion("Continue?");
        }
    }

    public void discoveryMission()
    {
        DiscoveryMission mission = new DiscoveryMission(this, 0);
        mission.run();
    }

    public void discoveryMissionWithPause()
    {
        DiscoveryMission mission = new DiscoveryMission(this, 1);
        mission.run();
    }

    public void hibernate(String message, String shortHello, String longHello)
    {
        say(message);
        sleepForSeconds(1);
        say("You can wake me up by pressing one of my red touch sensors or waving your hand in front of my eyes.");
        long startTime = elapsedTimeMilliseconds();
        sleepForSeconds(5); // Give time for hand to move away from eyes, or we'll just wake up straight away below.
        for (;;)
        {
            if (leftTouch() || rightTouch() || headDistance() <= MAXIMUM_HAND_SIGNAL_DISTANCE)
            {
                break;
            }
        }
        long finishTime = elapsedTimeMilliseconds();
        long timeAsleep = (finishTime - startTime) / 1000; // seconds
        if (timeAsleep < 60)
        {
            say(shortHello);
        }
        else
        {
            say(longHello);
        }
    }

    public void triangle()
    {
        calibrate();
        Point point = new Point(0, 0);
        for (;;)
        {
            moveStartingAt = point;
            moveForward(70);
            Point newPoint = moveFinishedAt;
            System.out.println("check dist = " + point.distanceTo(newPoint));
            if (!askYesNoQuestion("Continue?")) break;
            turnDegrees(-120);
        }
    }
}
