package de.intranda.goobi.plugins;

import java.io.Serializable;
import java.util.List;
import lombok.Setter;
import lombok.Getter;

/**
 * This class stores a step that should be closed. It contains the name of the step and a list of conditions. The conditions depend on other steps
 * that must have reached a certain status to allow to close this step.
 * 
 * @author maurice
 */
public class CloseableStep implements Serializable {

    /**
     * The serial version UID
     */
    public static final long serialVersionUID = 1L;

    /**
     * The name of the step to close
     */
    @Setter
    @Getter
    private String name;

    /**
     * The list of conditions. Each condition depends on one certain other step that must have reached a certain status.
     */
    @Setter
    @Getter
    private List<CloseCondition> conditions;

    /**
     * A constructor to get a CloseableStep object with name and list of conditions
     * 
     * @param name The name of the step to close
     * @param conditions The list of conditions
     */
    public CloseableStep(String name, List<CloseCondition> conditions) {
        this.name = name;
        this.conditions = conditions;
    }

    /**
     * Returns the string representation of a closeable step (only for debuggins purposes)
     *
     * @return The string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("STEP: Name: \"" + this.name + "\", ");
        sb.append("Conditions: [");
        int i = 0;
        while (i < this.conditions.size()) {
            sb.append(this.conditions.get(i).toString());
            if (i < this.conditions.size() - 1) {
                sb.append(", ");
            }
            i++;
        }
        sb.append("]");
        return sb.toString();
    }
}