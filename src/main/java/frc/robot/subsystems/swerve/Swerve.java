package frc.robot.subsystems.swerve;

import com.pathplanner.lib.auto.AutoBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.generic.GenericSubsystem;
import frc.lib.generic.OdometryThread;
import frc.lib.math.AdvancedSwerveKinematics;
import frc.lib.math.Optimizations;
import frc.lib.util.mirrorable.Mirrorable;
import frc.robot.RobotContainer;
import org.littletonrobotics.junction.AutoLogOutput;

import static frc.lib.math.Conversions.proportionalPowerToMps;
import static frc.lib.math.MathUtils.getAngleFromPoseToPose;
import static frc.robot.RobotContainer.POSE_ESTIMATOR;
import static frc.robot.subsystems.swerve.SwerveConstants.*;
import static frc.robot.subsystems.swerve.SwerveModuleConstants.MODULES;

public class Swerve extends GenericSubsystem {
    private double lastTimestamp = Timer.getFPGATimestamp();

    public Swerve() {
        configurePathPlanner();
    }

    public void stop() {
        for (SwerveModule currentModule : MODULES)
            currentModule.stop();
    }

    public void setGyroHeading(Rotation2d heading) {
        GYRO.setGyroYaw(heading.getDegrees());
    }

    public ChassisSpeeds getRobotRelativeVelocity() {
        return SWERVE_KINEMATICS.toChassisSpeeds(getModuleStates());
    }

    @Override
    public void periodic() {
        final double[] odometryUpdatesYawDegrees = GYRO.getInputs().threadGyroYawDegrees;
        final int odometryUpdates = odometryUpdatesYawDegrees.length;

        if (OdometryThread.getInstance().getLatestTimestamps().length == 0) return;

        final SwerveModulePosition[][] swerveWheelPositions = new SwerveModulePosition[odometryUpdates][];
        final Rotation2d[] gyroRotations = new Rotation2d[odometryUpdates];

        for (int i = 0; i < odometryUpdates; i++) {
            swerveWheelPositions[i] = getSwerveWheelPositions(i);
            gyroRotations[i] = Rotation2d.fromDegrees(odometryUpdatesYawDegrees[i]);
        }

        POSE_ESTIMATOR.updatePoseEstimatorStates(
                        swerveWheelPositions,
                        gyroRotations,
                        OdometryThread.getInstance().getLatestTimestamps()
                );
    }

    protected void driveOrientationBased(double xPower, double yPower, double thetaPower, boolean robotCentric) {
        if (robotCentric)
            driveRobotRelative(xPower, yPower, thetaPower);
        else
            driveFieldRelative(xPower, yPower, thetaPower);
    }

    protected void driveWithTarget(double xPower, double yPower, Pose2d target, boolean robotCentric) {
        final Rotation2d currentAngle = RobotContainer.POSE_ESTIMATOR.getCurrentPose().getRotation();
        final Rotation2d targetAngle = getAngleFromPoseToPose(RobotContainer.POSE_ESTIMATOR.getCurrentPose(), target);

        final double controllerOutput = Units.degreesToRadians(
                SWERVE_ROTATION_CONTROLLER.calculate(
                        currentAngle.getDegrees(),
                        targetAngle.getDegrees()
                ));

        if (robotCentric)
            driveRobotRelative(xPower, yPower, controllerOutput);
        else
            driveFieldRelative(xPower, yPower, controllerOutput);
    }

    protected void driveToPose(Pose2d target) {
        final Pose2d currentPose = POSE_ESTIMATOR.getCurrentPose();

        driveFieldRelative(
                SWERVE_TRANSLATION_CONTROLLER.calculate(
                        currentPose.getX(),
                        target.getX()
                ),

                SWERVE_TRANSLATION_CONTROLLER.calculate(
                        currentPose.getY(),
                        target.getY()
                ),

                SWERVE_ROTATION_CONTROLLER.calculate(
                        currentPose.getRotation().getDegrees(),
                        target.getRotation().getDegrees()
                )
        );
    }

    protected void driveFieldRelative(double xPower, double yPower, double thetaPower) {
        ChassisSpeeds speeds = proportionalSpeedToMps(new ChassisSpeeds(xPower, yPower, thetaPower));
        speeds = ChassisSpeeds.fromFieldRelativeSpeeds(speeds, RobotContainer.POSE_ESTIMATOR.getCurrentPose().getRotation());

        driveRobotRelative(speeds);
    }

    protected void driveRobotRelative(double xPower, double yPower, double thetaPower) {
        final ChassisSpeeds speeds = proportionalSpeedToMps(new ChassisSpeeds(xPower, yPower, thetaPower));
        driveRobotRelative(speeds);
    }

    protected void driveRobotRelative(ChassisSpeeds chassisSpeeds) {
        chassisSpeeds = discretize(chassisSpeeds);
        chassisSpeeds = AdvancedSwerveKinematics.correctForDynamics(chassisSpeeds);

        if (Optimizations.isStill(chassisSpeeds)) {
            stop();
            return;
        }

        final SwerveModuleState[] swerveModuleStates = SWERVE_KINEMATICS.toSwerveModuleStates(chassisSpeeds);

        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, MAX_SPEED_MPS);

        for (int i = 0; i < MODULES.length; i++)
            MODULES[i].setTargetState(swerveModuleStates[i]);
    }

    protected void initializeDrive(boolean openLoop) {
        for (SwerveModule currentModule : MODULES)
            currentModule.setOpenLoop(openLoop);

        SWERVE_ROTATION_CONTROLLER.reset(POSE_ESTIMATOR.getCurrentPose().getRotation().getDegrees());
    }

    protected SwerveModulePosition[] getSwerveWheelPositions(int odometryUpdateIndex) {
        final SwerveModulePosition[] swerveModulePositions = new SwerveModulePosition[MODULES.length];

        for (int i = 0; i < MODULES.length; i++) {
            swerveModulePositions[i] = MODULES[i].getOdometryPosition(odometryUpdateIndex);
        }

        return swerveModulePositions;
    }

    protected void configurePathPlanner() {
        AutoBuilder.configure(
                POSE_ESTIMATOR::getCurrentPose,
                POSE_ESTIMATOR::resetPose,
                this::getRobotRelativeVelocity,
                (speeds, feedforwards) -> driveRobotRelative(speeds),
                PATHPLANNER_PID_CONSTANTS,
                PATHPLANNER_ROBOT_CONFIGURATION,
                Mirrorable::isRedAlliance,
                this
        );
    }

    protected ChassisSpeeds proportionalSpeedToMps(ChassisSpeeds chassisSpeeds) {
        return new ChassisSpeeds(
                proportionalPowerToMps(chassisSpeeds.vxMetersPerSecond, MAX_SPEED_MPS),
                proportionalPowerToMps(chassisSpeeds.vyMetersPerSecond, MAX_SPEED_MPS),
                chassisSpeeds.omegaRadiansPerSecond
        );
    }

    @AutoLogOutput(key = "Swerve/CurrentStates")
    private SwerveModuleState[] getModuleStates() {
        final SwerveModuleState[] states = new SwerveModuleState[MODULES.length];

        for (int i = 0; i < MODULES.length; i++)
            states[i] = MODULES[i].getCurrentState();

        return states;
    }

    @AutoLogOutput(key = "Swerve/TargetStates")
    @SuppressWarnings("unused")
    protected SwerveModuleState[] getModuleTargetStates() {
        final SwerveModuleState[] states = new SwerveModuleState[MODULES.length];

        for (int i = 0; i < MODULES.length; i++)
            states[i] = MODULES[i].getTargetState();

        return states;
    }

    protected ChassisSpeeds discretize(ChassisSpeeds chassisSpeeds) {
        final double currentTimestamp = Timer.getFPGATimestamp();
        final double difference = currentTimestamp - lastTimestamp;
        lastTimestamp = currentTimestamp;

        return ChassisSpeeds.discretize(chassisSpeeds, difference);
    }
}
