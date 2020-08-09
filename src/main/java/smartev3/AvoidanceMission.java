package smartev3;

public class AvoidanceMission
{
    private SmartRobot robot;
    private org.slf4j.Logger logger;
    private int pauseInterval;

    public AvoidanceMission(SmartRobot robot, int pauseInterval)
    {
        this.robot = robot;
        this.logger = robot.logger;
        this.pauseInterval = pauseInterval;
    }

    public void run()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting avoidanceMission");
        }
        if (pauseInterval == 0)
        {
            if (robot.inputModeAllowsMultiChoice())
            {
                robot.say("I will pause every so often to allow you to direct the mission.");
                pauseInterval = robot.askUserForNumber("How many mission steps should I do before each pause?", 1, 9);
            }
            else
            {
                pauseInterval = 9;
            }
        }
        robot.calibrate();
        for (int pass = 1;; pass++)
        {
            // If pass == 1, then calibrate has already setup proximityMap.
            if (pass > 1)
            {
                robot.lookAround(); // To setup proximityMap.
            }
            boolean mustStepBack = false;
            for (int check = 1; check <= 5; check++)
            {
                float oldDistance = robot.proximityMap.getDistanceAtAngle(0);
                float newDistance = robot.headDistance();
                if (newDistance == 255)
                {
                    // Closer than around 5 cm cannot see obstacle so 
                    // ultrasonic sensor will return 255. Treat as 5
                    // to force "step back" below.
                    newDistance = 5;
                }
                if (newDistance < 15 && newDistance < oldDistance)
                {
                    mustStepBack = true;
                    robot.proximityMap.setDistanceAtAngle(0, newDistance);
                }
            }
            if (mustStepBack)
            {
                logger.debug("........ avoidanceMission (must step back)");
                robot.stepBackward(10);
                robot.lookAround();
            }
            // logger.debug("original proximity map = " + robot.proximityMap);
            ConsoleGrid originalGrid = robot.proximityMap.consoleGrid("Proximity Map (original)");
            robot.proximityMap.adjustForSafePassing();
            // logger.debug("adjusted proximity map = " + robot.proximityMap);
            ConsoleGrid adjustedGrid = robot.proximityMap.consoleGrid("Proximity Map (adjusted)");
            System.out.print(originalGrid.join(adjustedGrid));
            int angle = robot.proximityMap.angleWithMaximumDistance();
            float distance = robot.proximityMap.getDistanceAtAngle(angle);
            if (logger.isDebugEnabled())
            {
                logger.debug("........ avoidanceMission (target angle = "
                    + angle + ", distance = " + distance + ")");
            }
            angle = robot.proximityMap.middleWithSimilarDistance(distance);
            distance = robot.proximityMap.getDistanceAtAngle(angle);
            if (logger.isDebugEnabled())
            {
                logger.debug("........ avoidanceMission (middle angle = "
                    + angle + ", distance = " + distance + ")");
            }
            int oldAngle = robot.gyroAngle();
            int targetAngle = oldAngle + angle;
            robot.turnDegrees(angle);
            int newAngle = robot.gyroAngle();
            if (newAngle != targetAngle)
            {
                robot.turnDegrees(targetAngle - newAngle); // in case minor adjustment is needed
            }
            if (pass % pauseInterval == 0)
            {
                if (!robot.askYesNoQuestion("Should I continue the mission?"))
                {
                    break;
                }
            }
            robot.moveForward(distance - robot.safeStoppingDistance());
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished avoidanceMission");
        }
    }
}
