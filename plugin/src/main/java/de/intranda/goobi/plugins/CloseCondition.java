package de.intranda.goobi.plugins;

import de.sub.goobi.helper.enums.StepStatus;
import java.io.Serializable;
import lombok.Setter;
import lombok.Getter;

/**
 * This class stores a condition indicating when an other stop may be closed. You can create an object of this class to indicate that the step with
 * this name must reach the specified status to be allowed to close an other step.
 *
 * @author maurice
 */
public class CloseCondition implements Serializable {

    /**
     * The serial version UID
     */
    public static final long serialVersionUID = 1L;

    /**
     * The name of the step in the condition
     */
    @Setter
    @Getter
    private String stepName;

    /**
     * The status for the condition. When the step has this status, the condition is true
     */
    @Setter
    @Getter
    private StepStatus status;

    /**
     * A constructor to get a CloseCondition object
     *
     * @param stepName The name of the step that must have a specified status
     * @param status The status that must be reached
     */
    public CloseCondition(String stepName, StepStatus status) {
        this.stepName = stepName;
        this.status = status;
    }

    /**
     * Returns the string representation of the condition (only for debugging purposes)
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        return "Step \"" + this.stepName + "\" in status \"" + this.status.toString() + "\"";
    }
}