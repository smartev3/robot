package smartev3;

import java.util.*;

public class DiscoveryMission
{
    private static final int DISCOVERY_ANGLE = 135;

    private SmartRobot robot;
    private org.slf4j.Logger logger;
    private int pauseInterval;
    private DiscoveryMap discoveryMap;

    public DiscoveryMission(SmartRobot robot, int pauseInterval)
    {
        this.robot = robot;
        this.logger = robot.logger;
        this.pauseInterval = pauseInterval;
        this.discoveryMap = new DiscoveryMap();
    }

    public void run()
    {
        robot.discoveryMode = true;
        try
        {
            runDiscovery();
        }
        finally
        {
            robot.discoveryMode = false;
        }
    }

    private void runDiscovery()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Starting new discoveryMission");
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
        int relativeTo = 0;
        for (int pass = 1;; pass++)
        {
            if (pass > 1)
            {
                robot.lookAround(DISCOVERY_ANGLE, 1);
            }
            ProximityMap proximityMap = robot.proximityMap;
            ConsoleGrid proximityGrid = proximityMap.consoleGrid();
            if (pass > 1)
            {
                relativeTo = robot.gyroAngle();
            }
            discoveryMap.addObstacles(proximityMap, relativeTo);
            discoveryMap.chooseTarget();
            Cell target = discoveryMap.targetCell;
            if (target == null)
            {
                break;
            }
            if (logger.isDebugEnabled())
            {
                logger.debug("........ discoveryMission: cells to target = " + discoveryMap.cellsToTarget);
                logger.debug("........ discoveryMission: points to target = " + discoveryMap.pointsToTarget);
            }
            discoveryMap.smoothenPath();
            if (logger.isDebugEnabled())
            {
                logger.debug("........ discoveryMission: now cells to target = " + discoveryMap.cellsToTarget);
                logger.debug("........ discoveryMission: now points to target = " + discoveryMap.pointsToTarget);
            }
            ConsoleGrid discoveryGrid = discoveryMap.consoleGrid();
            System.out.println(proximityGrid.join(discoveryGrid));
            System.out.print(discoveryMap.nearObjects());
            if (pass % pauseInterval == 0)
            {
                if (!robot.askYesNoQuestion("Should I continue the mission?"))
                {
                    break;
                }
            }
            moveToTarget();
            discoveryMap.robotLocation = target.midpoint; // TODO: better estimate
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("Finished new discoveryMission");
        }
    }

    private void moveToTarget()
    {
        ArrayList<Point> pathToTarget = discoveryMap.pointsToTarget;
        boolean firstPoint = true;
        Point fromPoint = discoveryMap.robotLocation;
        for (Point toPoint : pathToTarget)
        {
            if (firstPoint) { firstPoint = false; continue; }
            double dy = toPoint.y - fromPoint.y;
            double dx = toPoint.x - fromPoint.x;
            double r = Math.atan2(dy, dx);
            int bearing = (int)-(MathHelper.radiansToDegrees(r) - 90); // compass bearing
            double distance = fromPoint.distanceTo(toPoint);
            logger.debug("fromPoint = " + fromPoint + ", toPoint = " + toPoint);
            logger.debug("    dx = " + dx + ", dy = " + dy + ", bearing = " + bearing + ", distance = " + SmartRobot.formatFixed1(distance));
            if (pauseInterval == 1) robot.askYesNoQuestion("Turn now?");
            robot.turnToBearing(bearing);
            if (pauseInterval == 1) robot.askYesNoQuestion("Move now?");
            try
            {
                robot.moveStartingAt = fromPoint;
                robot.moveForward((float)distance);
                Point finishedAt = robot.moveFinishedAt;
                discoveryMap.robotLocation = finishedAt;
                fromPoint = finishedAt;
                double locationDiff = toPoint.distanceTo(finishedAt);
                System.out.println("*** moveForward: distance error = " + SmartRobot.formatFixed1(locationDiff));
            }
            finally
            {
                robot.moveStartingAt = null;
                robot.moveFinishedAt = null;
            }
        }
    }
}
