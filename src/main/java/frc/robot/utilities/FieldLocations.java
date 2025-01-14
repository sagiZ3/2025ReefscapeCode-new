package frc.robot.utilities;

import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation3d;

public class FieldLocations {
    public static final Pose3d BLUE_TOP_FEEDER = new Pose3d(new Translation3d(1.172, 6.93446, 0.9525),
            new Rotation3d(0, 0, Math.toRadians(126)));
    public static final Pose3d BLUE_BOTTOM_FEEDER = new Pose3d(new Translation3d(1.172, 1.151, 0.9525),
            new Rotation3d(0, 0, Math.toRadians(234)));
}
