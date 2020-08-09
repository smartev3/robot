package smartev3;

import java.util.*;

public class ProximityMap
{
    public final int minimumAngle;
    public final int maximumAngle;

    private final SmartRobot robot;
    private final float[] array;
    private final float[] saveArray;

    public ProximityMap(SmartRobot robot, int minimumAngle, int maximumAngle)
    {
        this.robot = robot;
        this.minimumAngle = minimumAngle;
        this.maximumAngle = maximumAngle;
        this.array = new float[1 + maximumAngle - minimumAngle];
        this.saveArray = new float[1 + maximumAngle - minimumAngle];
        this.reset();
    }

    public void reset()
    {
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        for (int angle = min; angle <= max; angle++)
        {
            setDistanceAtAngle(angle, Float.NaN);
        }
    }

    public float getDistanceAtAngle(int angle)
    {
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        if (angle >= min && angle <= max)
        {
            return array[angle - min];
        }
        else
        {
            return Float.NaN;
        }
    }

    public void setDistanceAtAngle(int angle, float distance)
    {
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        if (angle >= min && angle <= max)
        {
            array[angle - minimumAngle] = distance;
        }
    }

    public int countDefinedDistances()
    {
        int count = 0;
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        for (int i = min; i <= max; i++)
        {
            float x = getDistanceAtAngle(i);
            if (!Float.isNaN(x)) count++;
        }
        return count;
    }

    public void adjustForSafePassing()
    {
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        float safeWidth = robot.safePassingWidth();
        for (int angle1 = min; angle1 <= max; angle1++)
        {
            float distance1 = getDistanceAtAngle(angle1);
            if (!Float.isNaN(distance1))
            {
                int angle2 = angle1 + 1;
                while (angle2 <= max)
                {
                    float distance2 = getDistanceAtAngle(angle2);
                    if (!Float.isNaN(distance2))
                    {
                        int anglesBetween = angle2 - angle1 - 1;
                        if (anglesBetween > 0)
                        {
                            double gap = MathHelper.distanceBetweenPolarDegrees(angle1, distance1, angle2, distance2);
                            if (gap < safeWidth)
                            {
                                float deltaDistance = distance2 - distance1;
                                for (int angle = angle1 + 1; angle < angle2; angle++)
                                {
                                    int step = angle - angle1; // 1, 2...
                                    float stepDistance = distance1 + step * (deltaDistance / (angle2 - angle1));
                                    setDistanceAtAngle(angle, stepDistance);
                                }
                            }
                        }
                        angle1 = angle2 - 1;
                        break;
                    }
                    angle2++;
                }
            }
        }
    }

    public int angleWithMaximumDistance()
    {
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        int resultAngle = 0;
        float maxDistance = 0.0f;
        for (int angle = min; angle <= max; angle++)
        {
            float distance = getDistanceAtAngle(angle);
            if (distance > maxDistance && !Float.isNaN(distance))
            {
                resultAngle = angle;
                maxDistance = distance;
            }
        }
        return resultAngle;
    }

    public List<Integer> anglesWithSimilarDistance(float distance)
    {
        List<Integer> result = new ArrayList<Integer>();
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        for (int i = min; i <= max; i++)
        {
            float x = getDistanceAtAngle(i);
            if (Float.isNaN(x)) continue;
            float compare = x / distance;
            if (compare >= 0.8 && compare <= 1.2)
            {
                // within 20% of distance
                result.add(i);
            }
        }
        return result;
    }

    public int middleWithSimilarDistance(float distance)
    {
        List<Integer> angles = anglesWithSimilarDistance(distance);
        int n = angles.size();
        return n > 0 ? angles.get(n / 2) : 0;
    }

    public ConsoleGrid consoleGrid()
    {
        return consoleGrid("Proximity Map");
    }

    public ConsoleGrid consoleGrid(String label)
    {
        int rows = ConsoleGrid.ROWS;
        int cols = ConsoleGrid.COLS;
        char[][] array = ConsoleGrid.newArray(ConsoleGrid.ROWS, ConsoleGrid.COLS);
        int min = this.minimumAngle;
        int max = this.maximumAngle;
        float limit = 255f;
        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; col++)
            {
                array[row][col] = ' ';
            }
        }
        int amd = angleWithMaximumDistance();
        for (int angle = min; angle <= max; angle++)
        {
            float d = getDistanceAtAngle(angle);
            if (Float.isNaN(d)) continue;
            double x = Math.cos(MathHelper.degreesToRadians(angle - 90)) * d / limit; // -1 to 1
            double y = Math.sin(MathHelper.degreesToRadians(angle - 90)) * d / limit; // -1 to 1
            x = Math.max(-1, Math.min(1, x));
            y = Math.max(-1, Math.min(1, y));
            int r = Math.min(rows - 1, (int)Math.round((y + 1) * (rows / 2)));
            int c = Math.min(cols - 1, (int)Math.round((x + 1) * (cols / 2)));
            if (array[r][c] != '$')
            {
                array[r][c] = (angle == amd) ? '$' : 'O';
            }
        }
        // Mark centre (robot position) with 2x2 square of '#'.
        int hr = rows / 2;
        int hc = cols / 2;
        array[hr][ hc] = '#';
        array[hr][hc + 1] = '#';
        array[hr + 1][hc] = '#';
        array[hr + 1][hc + 1] = '#';
        return new ConsoleGrid(array, label);
    }

    public String toString()
    {
        StringBuilder text = new StringBuilder();
        text.append("{");
        text.append("count: " + countDefinedDistances());
        int min = minimumAngle;
        int max = maximumAngle;
        for (int angle = min; angle <= max; angle++)
        {
            float distance = getDistanceAtAngle(angle);
            if (Float.isNaN(distance)) continue;
            text.append(", ");
            text.append(angle);
            text.append(": ");
            text.append(robot.formatFixed1(distance));
        }
        text.append("}");
        return text.toString();
    }
}
