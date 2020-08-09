package smartev3;

import java.util.*;

import smartev3.ProximityMap;

public class SimpleMoves
{
    private SmartRobot robot;
    private List<Action> route = new ArrayList<Action>();
    private int targetAngle;

    enum ActionKind
    {
        MOVE,
        TURN;
    }

    class Action
    {
        ActionKind kind;
        float number;
    }

    public SimpleMoves(SmartRobot robot)
    {
        this.robot = robot;
    }

    public void run()
    {
        robot.say("Please use voice commands to direct my mission.");
        robot.say("I can go forward from one to nine metres.");
        robot.say("I can step forward or step back. Each step is 10 centimetres.");
        robot.say("I can turn left or turn right. Each turn step is 10 degrees.");
        robot.say("I can turn around, to face the other way.");
        robot.say("So the voice commands are: go forward, step forward, step back, turn left, turn right, turn around, and return to base.");
        robot.say("When you are finished, say: stop mission.");
        robot.say("I'll just check out my surroundings.");
        targetAngle = robot.gyroAngle() % 360;
        robot.calibrate();
        System.out.println(robot.proximityMap.consoleGrid().toString());
        robot.say("OK, I'm ready for my mission.");
        updateAngle(0);
        List<String> choices = new ArrayList<String>();
        choices.add("go forward");
        choices.add("step forward");
        choices.add("step back");
        choices.add("turn left");
        choices.add("turn right");
        choices.add("turn around");
        choices.add("return to base");
        choices.add("stop mission");
        WordAliases aliases = new WordAliases()
            .addStop()
            .addTurn()
            .addLeft()
            .addRight()
            .add("back", "backward", "backwards")
            .add("forward", "ford", "fort", "forwards", "foreword", "for it")
            .add("mission", "motion");
        for (int pass = 1;; pass++)
        {
            int choice = robot.askUserToChoose("What should I do "
                + (pass == 1 ? "first" : "next")
                + "?", choices, aliases);
            switch (choice)
            {
                case 1: goForward(); break;
                case 2: stepForward(); break;
                case 3: stepBackward(); break;
                case 4: turnLeft(); break;
                case 5: turnRight(); break;
                case 6: turnAround(); break;
                case 7: returnToBase(); break;
                case 8: return;
            }
            robot.lookAround();
            System.out.println(robot.proximityMap.consoleGrid().toString());
        }
    }

    private void goForward()
    {
        int metres = robot.askUserForNumber("How many metres?", 1, 9);
        int distance = metres * 100;
        robot.say("Moving forward " + metres + " metres now.");
        float traveled = robot.moveForward(distance);
        addAction(ActionKind.MOVE, traveled);
        updateAngle(0);
        if (traveled + 10 <= distance)
        {
            robot.say("I had to stop after " + SmartRobot.formatFixed1(traveled / 100f)
                + " metres due to an obstacle.");
        }
    }

    private void stepForward()
    {
        int steps = robot.askUserForNumber("How many steps?", 1, 9);
        int distance = steps * 10;
        robot.say("Taking " + steps + " step"
            + (steps == 1 ? "" : "s")+ " forward now.");
        float traveled = robot.moveForward(distance);
        addAction(ActionKind.MOVE, traveled);
        updateAngle(0);
        if (traveled + 10 <= distance)
        {
            robot.say("I had to stop after " + (int)traveled
                + " centimetres due to an obstacle.");
        }
    }

    private void stepBackward()
    {
        int steps = robot.askUserForNumber("How many steps?", 1, 9);
        int distance = steps * 10;
        robot.say("Taking " + steps + " step"
            + (steps == 1 ? "" : "s") + " back now.");
        robot.stepBackward(distance);
        addAction(ActionKind.MOVE, -distance);
        updateAngle(0);
    }

    private void turnLeft()
    {
        int steps = robot.askUserForNumber("How many turn steps?", 1, 9);
        int angle = steps * 10;
        robot.say("Turning left " + steps + " step"
            + (steps == 1 ? "" : "s") + " now. ");
        robot.turnDegrees(-angle);
        addAction(ActionKind.TURN, -angle);
        updateAngle(-angle);
    }

    private void turnRight()
    {
        int steps = robot.askUserForNumber("How many turn steps?", 1, 9);
        int angle = steps * 10;
        robot.say("Turning right " + steps + " step"
            + (steps == 1 ? "" : "s") + " now.");
        robot.turnDegrees(angle);
        addAction(ActionKind.TURN, angle);
        updateAngle(angle);
    }

    private void turnAround()
    {
        robot.say("Turning around now.");
        int angle = 180;
        robot.turnDegrees(angle);
        addAction(ActionKind.TURN, angle);
        updateAngle(angle);
    }
    
    private void returnToBase()
    {
        robot.say("I am returning to base.");
        addAction(ActionKind.TURN, 180);
        Collections.reverse(route);
        addAction(ActionKind.TURN, 180);
        for (Action action : route)
        {
            switch (action.kind)
            {
                case MOVE:
                    float distance = action.number, traveled;
                    if (distance < 0)
                    {
                        distance = -distance;
                        traveled = robot.stepBackward(distance);
                    }
                    else
                    {
                        traveled = robot.moveForward(distance);
                    }
                    if (traveled + 10 <= distance)
                    {
                        robot.say("Sorry. Due to an unexpected obstacle, I am unable to return to base.");
                        route.clear();
                        return;
                    }
                    updateAngle(0);
                    break;
                case TURN:
                    int angle = -(int)action.number;
                    robot.turnDegrees(angle);
                    updateAngle(angle);
                    break;
            }
        }
        route.clear();
    }

    private void addAction(ActionKind kind, float number)
    {
        if (!route.isEmpty())
        {
            Action last = route.get(route.size() - 1);
            if (last.kind == kind)
            {
                // Merge multiple adjacent steps of the same kind into one.
                last.number += number;
                return;
            }
        }
        Action action = new Action();
        action.kind = kind;
        action.number = number;
        route.add(action);
    }

    private void updateAngle(int angleDelta)
    {
        targetAngle = (targetAngle + angleDelta) % 360;
        int currentAngle = robot.gyroAngle() % 360;
        if (currentAngle != targetAngle)
        {
            robot.turnDegrees(targetAngle - currentAngle);
        }
    }
}
