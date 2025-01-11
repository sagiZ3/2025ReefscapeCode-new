package frc.robot;

import com.pathplanner.lib.path.PathPlannerPath;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.lib.generic.hardware.HardwareManager;
import org.json.simple.parser.ParseException;
import org.littletonrobotics.junction.LoggedRobot;

import java.io.IOException;

import static frc.robot.RobotContainer.LEDS;
import static frc.robot.RobotContainer.POSE_ESTIMATOR;
import static frc.robot.poseestimation.photoncamera.CameraFactory.VISION_SIMULATION;

public class Robot extends LoggedRobot {
    private final CommandScheduler commandScheduler = CommandScheduler.getInstance();
    private RobotContainer robotContainer;

    private final Field2d simulatedVisionField = VISION_SIMULATION.getDebugField();

    @Override
    public void robotInit() {
        robotContainer = new RobotContainer();
        HardwareManager.initialize(this);
    }

    @Override
    public void robotPeriodic() {
        HardwareManager.update();
        commandScheduler.run();

        POSE_ESTIMATOR.periodic();
    }

    @Override
    public void disabledInit() {
    }

    @Override
    public void disabledPeriodic() {
        try {
            final PathPlannerPath path = PathPlannerPath.fromPathFile(robotContainer.getAutoName());

            if (path.getStartingHolonomicPose().isEmpty()) return;

            final Translation2d startingTranslation = path.getStartingHolonomicPose().get().getTranslation();

            LEDS.setLEDToPositionIndicator(
                    POSE_ESTIMATOR.getCurrentPose().getTranslation(),
                    startingTranslation
            );

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void disabledExit() {
    }

    @Override
    public void autonomousInit() {
        final Command autonomousCommand = robotContainer.getAutonomousCommand();

        if (autonomousCommand != null) {
            autonomousCommand.schedule();
        }
    }

    @Override
    public void autonomousPeriodic() {
    }

    @Override
    public void autonomousExit() {
    }

    @Override
    public void teleopInit() {
    }

    @Override
    public void teleopPeriodic() {
    }

    @Override
    public void teleopExit() {
    }

    @Override
    public void testInit() {
        CommandScheduler.getInstance().cancelAll();
    }

    @Override
    public void simulationPeriodic() {
        HardwareManager.updateSimulation();

        VISION_SIMULATION.updateRobotPose(POSE_ESTIMATOR.getOdometryPose());
        simulatedVisionField.getObject("EstimatedRobot").setPose(POSE_ESTIMATOR.getCurrentPose());
    }

    @Override
    public void close() {
        super.close();
    }
}
